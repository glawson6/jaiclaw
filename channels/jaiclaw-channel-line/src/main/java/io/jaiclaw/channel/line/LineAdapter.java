package io.jaiclaw.channel.line;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.channel.*;
import io.jaiclaw.channel.chunking.PlatformLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LINE messaging channel adapter.
 *
 * <p>Uses the LINE Messaging API for outbound messages (push/reply) and
 * processes inbound webhook events from LINE's platform. The LINE Bot SDK
 * provides message types and event parsing.
 *
 * <p>Inbound: webhook events are dispatched via {@link #processWebhookPayload(String, String)}.
 * Outbound: uses LINE Messaging API push endpoint via HTTP client.
 */
public class LineAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(LineAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LINE_API_BASE = "https://api.line.me/v2/bot";

    private final LineConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ChannelMessageHandler handler;
    private HttpClient httpClient;

    public LineAdapter(LineConfig config) {
        this.config = config;
    }

    // Visible for testing
    LineAdapter(LineConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    @Override
    public String channelId() {
        return "line";
    }

    @Override
    public String displayName() {
        return "LINE";
    }

    @Override
    public PlatformLimits platformLimits() {
        return PlatformLimits.LINE;
    }

    @Override
    public void start(ChannelMessageHandler handler) {
        this.handler = handler;
        if (httpClient == null) {
            httpClient = HttpClient.newHttpClient();
        }
        running.set(true);
        log.info("LINE adapter started");
    }

    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        try {
            // Check if we have a replyToken to use reply API, otherwise use push API
            String replyToken = message.platformData() != null
                    ? (String) message.platformData().get("replyToken")
                    : null;

            if (replyToken != null && !replyToken.isEmpty()) {
                return sendReply(replyToken, message.content());
            } else {
                return sendPush(message.peerId(), message.content());
            }
        } catch (Exception e) {
            log.error("Failed to send LINE message to {}", message.peerId(), e);
            return new DeliveryResult.Failure("send_failed", e.getMessage(), true);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        log.info("LINE adapter stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Process a LINE webhook payload. Called by the webhook endpoint.
     *
     * @param body      the raw request body
     * @param signature the X-Line-Signature header value
     */
    public void processWebhookPayload(String body, String signature) {
        if (body == null || body.isEmpty()) return;

        // Verify signature if channel secret is configured
        if (!config.channelSecret().isEmpty() && signature != null) {
            if (!verifySignature(body, signature)) {
                log.warn("LINE webhook signature verification failed");
                return;
            }
        }

        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode events = root.path("events");
            if (!events.isArray()) return;

            for (JsonNode event : events) {
                processEvent(event);
            }
        } catch (Exception e) {
            log.warn("Failed to process LINE webhook payload: {}", e.getMessage());
        }
    }

    void processEvent(JsonNode event) {
        String userId = event.path("source").path("userId").asText("");
        if (userId.isEmpty()) return;

        if (!config.isSenderAllowed(userId)) {
            log.debug("Dropping message from non-allowed LINE sender {}", userId);
            return;
        }

        ChannelMessage channelMessage = LineMessageMapper.mapEvent(event);
        if (channelMessage != null && handler != null) {
            handler.onMessage(channelMessage);
        }
    }

    boolean verifySignature(String body, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.channelSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hash);
            return computed.equals(signature);
        } catch (Exception e) {
            log.warn("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private DeliveryResult sendReply(String replyToken, String text) throws Exception {
        String json = MAPPER.writeValueAsString(Map.of(
                "replyToken", replyToken,
                "messages", new Object[]{Map.of("type", "text", "text", text)}
        ));

        var request = HttpRequest.newBuilder()
                .uri(URI.create(LINE_API_BASE + "/message/reply"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.channelAccessToken())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return new DeliveryResult.Success(UUID.randomUUID().toString());
        } else {
            return new DeliveryResult.Failure("line_api_error", "HTTP " + response.statusCode(), true);
        }
    }

    private DeliveryResult sendPush(String userId, String text) throws Exception {
        String json = MAPPER.writeValueAsString(Map.of(
                "to", userId,
                "messages", new Object[]{Map.of("type", "text", "text", text)}
        ));

        var request = HttpRequest.newBuilder()
                .uri(URI.create(LINE_API_BASE + "/message/push"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.channelAccessToken())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return new DeliveryResult.Success(UUID.randomUUID().toString());
        } else {
            return new DeliveryResult.Failure("line_api_error", "HTTP " + response.statusCode(), true);
        }
    }
}
