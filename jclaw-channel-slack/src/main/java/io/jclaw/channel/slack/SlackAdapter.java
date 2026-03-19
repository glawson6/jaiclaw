package io.jclaw.channel.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jclaw.channel.*;
import io.jclaw.gateway.WebhookDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Slack channel adapter with two inbound modes:
 *
 * <p><b>Socket Mode</b> (local dev): Uses Slack's Socket Mode WebSocket API.
 * No public endpoint needed. Activated when {@code appToken} (xapp-...) is set.
 *
 * <p><b>Events API webhook</b> (production): Receives events via POST /webhook/slack.
 * Activated when {@code appToken} is blank.
 *
 * <p>Outbound: Always posts via Slack chat.postMessage API.
 */
public class SlackAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(SlackAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SLACK_API_BASE = "https://slack.com/api/";

    private final SlackConfig config;
    private final WebhookDispatcher webhookDispatcher;
    private final RestTemplate restTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<WebSocket> socketModeWs = new AtomicReference<>();
    private ChannelMessageHandler handler;
    private Thread socketModeReconnectThread;

    public SlackAdapter(SlackConfig config, WebhookDispatcher webhookDispatcher) {
        this(config, webhookDispatcher, new RestTemplate());
    }

    public SlackAdapter(SlackConfig config, WebhookDispatcher webhookDispatcher,
                        RestTemplate restTemplate) {
        this.config = config;
        this.webhookDispatcher = webhookDispatcher;
        this.restTemplate = restTemplate;
    }

    @Override
    public String channelId() {
        return "slack";
    }

    @Override
    public String displayName() {
        return "Slack";
    }

    @Override
    public void start(ChannelMessageHandler handler) {
        this.handler = handler;

        if (config.useSocketMode()) {
            startSocketMode();
            log.info("Slack adapter started in SOCKET MODE (no public endpoint needed)");
        } else {
            webhookDispatcher.register("slack", this::handleWebhook);
            log.info("Slack adapter started in WEBHOOK mode (Events API)");
        }

        running.set(true);
    }

    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        try {
            String url = SLACK_API_BASE + "chat.postMessage";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.botToken());

            Map<String, Object> body = Map.of(
                    "channel", message.peerId(),
                    "text", message.content()
            );

            var request = new HttpEntity<>(body, headers);
            var response = restTemplate.postForEntity(url, request, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseBody = response.getBody();
                if (responseBody.path("ok").asBoolean()) {
                    String ts = responseBody.path("ts").asText();
                    return new DeliveryResult.Success(ts, Map.of("ts", ts));
                } else {
                    String error = responseBody.path("error").asText("unknown_error");
                    return new DeliveryResult.Failure(error, "Slack API error: " + error,
                            "ratelimited".equals(error));
                }
            } else {
                return new DeliveryResult.Failure(
                        "slack_api_error",
                        "HTTP " + response.getStatusCode(),
                        true);
            }
        } catch (Exception e) {
            log.error("Failed to send Slack message to {}", message.peerId(), e);
            return new DeliveryResult.Failure("send_failed", e.getMessage(), true);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        WebSocket ws = socketModeWs.getAndSet(null);
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutting down");
        }
        if (socketModeReconnectThread != null) {
            socketModeReconnectThread.interrupt();
        }
        log.info("Slack adapter stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // --- Socket Mode ---

    private void startSocketMode() {
        socketModeReconnectThread = Thread.ofVirtual().name("slack-socket-mode").start(() -> {
            while (running.get() || !Thread.currentThread().isInterrupted()) {
                try {
                    connectSocketMode();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("Slack Socket Mode error (will reconnect): {}", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("Slack Socket Mode reconnect loop stopped");
        });
    }

    /** Calls apps.connections.open to get a WebSocket URL, then connects. */
    void connectSocketMode() throws Exception {
        String wsUrl = openSocketModeConnection();
        if (wsUrl == null) {
            throw new IllegalStateException("Failed to obtain Socket Mode WebSocket URL");
        }

        log.info("Connecting to Slack Socket Mode WebSocket...");

        var latch = new java.util.concurrent.CountDownLatch(1);

        HttpClient client = HttpClient.newHttpClient();
        WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {

                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        log.info("Slack Socket Mode connected");
                        socketModeWs.set(webSocket);
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            String message = buffer.toString();
                            buffer.setLength(0);
                            handleSocketModeMessage(message, webSocket);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        log.info("Slack Socket Mode disconnected: {} {}", statusCode, reason);
                        socketModeWs.set(null);
                        latch.countDown();
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        log.warn("Slack Socket Mode WebSocket error: {}", error.getMessage());
                        socketModeWs.set(null);
                        latch.countDown();
                    }
                }).join();

        // Block until disconnected, then the reconnect loop retries
        latch.await();
    }

    /** Calls apps.connections.open with the app-level token to get a wss:// URL. */
    String openSocketModeConnection() {
        try {
            String url = SLACK_API_BASE + "apps.connections.open";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBearerAuth(config.appToken());

            var request = new HttpEntity<>("", headers);
            var response = restTemplate.postForEntity(url, request, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();
                if (body.path("ok").asBoolean()) {
                    return body.path("url").asText();
                } else {
                    log.error("Slack apps.connections.open failed: {}", body.path("error").asText());
                }
            }
        } catch (Exception e) {
            log.error("Failed to open Slack Socket Mode connection: {}", e.getMessage());
        }
        return null;
    }

    /** Processes a Socket Mode envelope (JSON from the WebSocket). */
    void handleSocketModeMessage(String message, WebSocket ws) {
        try {
            JsonNode envelope = MAPPER.readTree(message);
            String type = envelope.path("type").asText();

            // Acknowledge the envelope immediately
            String envelopeId = envelope.path("envelope_id").asText("");
            if (!envelopeId.isEmpty()) {
                String ack = MAPPER.writeValueAsString(Map.of("envelope_id", envelopeId));
                ws.sendText(ack, true);
            }

            if ("hello".equals(type)) {
                log.debug("Slack Socket Mode hello received");
                return;
            }

            if ("disconnect".equals(type)) {
                log.info("Slack Socket Mode disconnect requested, will reconnect");
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "reconnecting");
                return;
            }

            if ("events_api".equals(type)) {
                JsonNode payload = envelope.path("payload");
                processEventPayload(payload);
            }

        } catch (Exception e) {
            log.error("Failed to process Slack Socket Mode message", e);
        }
    }

    /** Processes an Events API payload (shared between webhook and Socket Mode). */
    private void processEventPayload(JsonNode payload) {
        String payloadType = payload.path("type").asText();

        if (!"event_callback".equals(payloadType)) {
            return;
        }

        JsonNode event = payload.path("event");
        String eventType = event.path("type").asText();

        // Only handle message events (not bot messages or subtypes)
        if ("message".equals(eventType) && !event.has("subtype") && !event.has("bot_id")) {
            String userId = event.path("user").asText();
            if (!config.isSenderAllowed(userId)) {
                log.debug("Dropping message from non-allowed Slack user {}", userId);
                return;
            }

            String text = event.path("text").asText();
            String channel = event.path("channel").asText();
            String teamId = payload.path("team_id").asText();
            String eventId = payload.path("event_id").asText();

            Map<String, Object> platformData = Map.of(
                    "team_id", teamId,
                    "channel", channel,
                    "ts", event.path("ts").asText(),
                    "event_id", eventId
            );

            var channelMessage = ChannelMessage.inbound(
                    eventId, "slack", teamId, channel, text, platformData);

            if (handler != null) {
                handler.onMessage(channelMessage);
            }
        }
    }

    // --- Webhook mode (Events API) ---

    private ResponseEntity<String> handleWebhook(String body, Map<String, String> headers) {
        try {
            JsonNode payload = MAPPER.readTree(body);
            String type = payload.path("type").asText();

            // Handle Slack URL verification challenge
            if ("url_verification".equals(type)) {
                String challenge = payload.path("challenge").asText();
                return ResponseEntity.ok(challenge);
            }

            // Handle event callbacks
            if ("event_callback".equals(type)) {
                processEventPayload(payload);
            }

            return ResponseEntity.ok("");
        } catch (Exception e) {
            log.error("Failed to process Slack webhook", e);
            return ResponseEntity.ok("");
        }
    }
}
