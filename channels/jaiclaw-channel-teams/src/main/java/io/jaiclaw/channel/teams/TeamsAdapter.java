package io.jaiclaw.channel.teams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.channel.*;
import io.jaiclaw.gateway.WebhookDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Microsoft Teams channel adapter using the Bot Framework REST API.
 *
 * <p>Inbound: Webhook-only — receives HTTP POST with JWT-signed Activity payloads
 * via the {@link WebhookDispatcher} at {@code /webhook/teams}.
 *
 * <p>Outbound: Posts replies via the Bot Framework REST API
 * ({@code {serviceUrl}/v3/conversations/{conversationId}/activities/{replyToId}})
 * using an Azure AD OAuth 2.0 bearer token.
 */
public class TeamsAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(TeamsAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TeamsConfig config;
    private final WebhookDispatcher webhookDispatcher;
    private final RestTemplate restTemplate;
    private final TeamsTokenManager tokenManager;
    private final TeamsJwtValidator jwtValidator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, String> serviceUrlCache = new ConcurrentHashMap<>();
    private ChannelMessageHandler handler;

    public TeamsAdapter(TeamsConfig config, WebhookDispatcher webhookDispatcher) {
        this(config, webhookDispatcher, new RestTemplate());
    }

    public TeamsAdapter(TeamsConfig config, WebhookDispatcher webhookDispatcher,
                        RestTemplate restTemplate) {
        this.config = config;
        this.webhookDispatcher = webhookDispatcher;
        this.restTemplate = restTemplate;
        this.tokenManager = new TeamsTokenManager(config.appId(), config.appSecret(), restTemplate);
        this.jwtValidator = new TeamsJwtValidator(config.appId(), restTemplate);
    }

    @Override
    public String channelId() {
        return "teams";
    }

    @Override
    public String displayName() {
        return "Microsoft Teams";
    }

    @Override
    public void start(ChannelMessageHandler handler) {
        this.handler = handler;
        webhookDispatcher.register("teams", this::handleWebhook);
        running.set(true);
        log.info("Teams adapter started (webhook mode)");
    }

    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        try {
            String conversationId = message.peerId();
            String serviceUrl = resolveServiceUrl(message);
            if (serviceUrl == null) {
                return new DeliveryResult.Failure(
                        "no_service_url",
                        "No serviceUrl cached for conversation " + conversationId,
                        false);
            }

            // Normalize serviceUrl — strip trailing slash
            if (serviceUrl.endsWith("/")) {
                serviceUrl = serviceUrl.substring(0, serviceUrl.length() - 1);
            }

            String replyToId = message.platformData() != null
                    ? String.valueOf(message.platformData().getOrDefault("activityId", ""))
                    : "";

            String url;
            if (replyToId.isEmpty()) {
                url = serviceUrl + "/v3/conversations/" + conversationId + "/activities";
            } else {
                url = serviceUrl + "/v3/conversations/" + conversationId + "/activities/" + replyToId;
            }

            String token = tokenManager.getAccessToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            Map<String, Object> activity = Map.of(
                    "type", "message",
                    "text", message.content()
            );

            var request = new HttpEntity<>(activity, headers);
            var response = restTemplate.postForEntity(url, request, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String activityId = response.getBody().path("id").asText("");
                return new DeliveryResult.Success(activityId, Map.of("activityId", activityId));
            } else {
                return new DeliveryResult.Failure(
                        "teams_api_error",
                        "HTTP " + response.getStatusCode(),
                        true);
            }
        } catch (Exception e) {
            log.error("Failed to send Teams message to {}", message.peerId(), e);
            return new DeliveryResult.Failure("send_failed", e.getMessage(), true);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        log.info("Teams adapter stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // --- Webhook handling ---

    ResponseEntity<String> handleWebhook(String body, Map<String, String> headers) {
        try {
            // Validate JWT unless skipped for testing
            if (!config.skipJwtValidation()) {
                String authHeader = headers.getOrDefault("authorization",
                        headers.getOrDefault("Authorization", ""));
                if (authHeader.isEmpty()) {
                    log.warn("Teams webhook missing Authorization header");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
                }

                String token = authHeader.startsWith("Bearer ")
                        ? authHeader.substring(7)
                        : authHeader;

                if (!jwtValidator.validate(token)) {
                    log.warn("Teams webhook JWT validation failed");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
                }
            }

            JsonNode activity = MAPPER.readTree(body);
            String type = activity.path("type").asText();

            // Cache serviceUrl for outbound replies
            String conversationId = activity.path("conversation").path("id").asText("");
            String serviceUrl = activity.path("serviceUrl").asText("");
            if (!conversationId.isEmpty() && !serviceUrl.isEmpty()) {
                serviceUrlCache.put(conversationId, serviceUrl);
            }

            return switch (type) {
                case "message" -> handleMessageActivity(activity);
                case "conversationUpdate" -> handleConversationUpdate(activity);
                case "invoke" -> ResponseEntity.ok("{\"status\": 200}");
                default -> {
                    log.debug("Ignoring Teams activity type: {}", type);
                    yield ResponseEntity.ok("");
                }
            };

        } catch (Exception e) {
            log.error("Failed to process Teams webhook", e);
            return ResponseEntity.ok("");
        }
    }

    private ResponseEntity<String> handleMessageActivity(JsonNode activity) {
        // Ignore bot's own messages
        JsonNode from = activity.path("from");
        String fromRole = from.path("role").asText("");
        if ("bot".equalsIgnoreCase(fromRole)) {
            log.debug("Ignoring bot's own message");
            return ResponseEntity.ok("");
        }

        // Apply sender allowlist on aadObjectId
        String aadObjectId = from.path("aadObjectId").asText("");
        if (!config.isSenderAllowed(aadObjectId)) {
            log.debug("Dropping message from non-allowed Teams user {}", aadObjectId);
            return ResponseEntity.ok("");
        }

        String text = activity.path("text").asText("").trim();
        if (text.isEmpty()) {
            log.debug("Ignoring Teams message with empty text");
            return ResponseEntity.ok("");
        }

        // Strip bot mention from text (Teams prepends <at>BotName</at>)
        text = text.replaceAll("<at>[^<]*</at>\\s*", "").trim();
        if (text.isEmpty()) {
            log.debug("Ignoring Teams message with only bot mention");
            return ResponseEntity.ok("");
        }

        String activityId = activity.path("id").asText("");
        String conversationId = activity.path("conversation").path("id").asText("");
        String tenantId = activity.path("channelData").path("tenant").path("id").asText(
                activity.path("conversation").path("tenantId").asText(""));
        String serviceUrl = activity.path("serviceUrl").asText("");
        String fromId = from.path("id").asText("");
        String fromName = from.path("name").asText("");

        Map<String, Object> platformData = Map.of(
                "activityId", activityId,
                "serviceUrl", serviceUrl,
                "tenantId", tenantId,
                "conversationId", conversationId,
                "fromId", fromId,
                "fromName", fromName,
                "aadObjectId", aadObjectId
        );

        // accountId = tenantId, peerId = conversationId
        var channelMessage = ChannelMessage.inbound(
                activityId, "teams", tenantId, conversationId, text, platformData);

        // Download file attachments
        List<ChannelMessage.Attachment> attachments = extractAttachments(activity);
        if (!attachments.isEmpty()) {
            channelMessage = ChannelMessage.inbound(
                    activityId, "teams", tenantId, conversationId, text, attachments, platformData);
        }

        if (handler != null) {
            handler.onMessage(channelMessage);
        }

        return ResponseEntity.ok("");
    }

    private ResponseEntity<String> handleConversationUpdate(JsonNode activity) {
        JsonNode membersAdded = activity.path("membersAdded");
        if (membersAdded.isArray()) {
            for (JsonNode member : membersAdded) {
                String memberId = member.path("id").asText("");
                log.info("Teams bot added to conversation, member: {}", memberId);
            }
        }
        return ResponseEntity.ok("");
    }

    private List<ChannelMessage.Attachment> extractAttachments(JsonNode activity) {
        JsonNode attachmentsNode = activity.path("attachments");
        if (!attachmentsNode.isArray() || attachmentsNode.isEmpty()) {
            return List.of();
        }

        List<ChannelMessage.Attachment> result = new ArrayList<>();
        for (JsonNode att : attachmentsNode) {
            String contentType = att.path("contentType").asText("");

            // Teams file download info attachment
            if ("application/vnd.microsoft.teams.file.download.info".equals(contentType)) {
                String name = att.path("name").asText("file");
                String downloadUrl = att.path("content").path("downloadUrl").asText("");
                if (!downloadUrl.isEmpty()) {
                    try {
                        byte[] data = restTemplate.getForObject(downloadUrl, byte[].class);
                        result.add(new ChannelMessage.Attachment(name, contentType, downloadUrl, data));
                    } catch (Exception e) {
                        log.warn("Failed to download Teams attachment {}: {}", name, e.getMessage());
                    }
                }
            }
        }
        return result;
    }

    private String resolveServiceUrl(ChannelMessage message) {
        // Try platformData first
        if (message.platformData() != null) {
            Object url = message.platformData().get("serviceUrl");
            if (url instanceof String s && !s.isEmpty()) {
                return s;
            }
        }
        // Fall back to cache
        return serviceUrlCache.get(message.peerId());
    }
}
