package io.jclaw.channel.discord;

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
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Discord channel adapter with two inbound modes:
 *
 * <p><b>Gateway WebSocket</b> (local dev): Connects to Discord Gateway for real-time events.
 * No public endpoint needed. Activated when {@code useGateway} is true.
 *
 * <p><b>Interactions webhook</b> (production): Receives interactions via POST /webhook/discord.
 * Activated when {@code useGateway} is false.
 *
 * <p>Outbound: Always sends via Discord REST API channels/{id}/messages.
 */
public class DiscordAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DISCORD_API_BASE = "https://discord.com/api/v10/";
    private static final int GATEWAY_VERSION = 10;

    private final DiscordConfig config;
    private final WebhookDispatcher webhookDispatcher;
    private final RestTemplate restTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<WebSocket> gatewayWs = new AtomicReference<>();
    private final AtomicReference<String> sessionId = new AtomicReference<>();
    private final AtomicReference<Integer> lastSequence = new AtomicReference<>(null);
    private ChannelMessageHandler handler;
    private Thread gatewayReconnectThread;
    private ScheduledExecutorService heartbeatExecutor;

    public DiscordAdapter(DiscordConfig config, WebhookDispatcher webhookDispatcher) {
        this(config, webhookDispatcher, new RestTemplate());
    }

    public DiscordAdapter(DiscordConfig config, WebhookDispatcher webhookDispatcher,
                          RestTemplate restTemplate) {
        this.config = config;
        this.webhookDispatcher = webhookDispatcher;
        this.restTemplate = restTemplate;
    }

    @Override
    public String channelId() {
        return "discord";
    }

    @Override
    public String displayName() {
        return "Discord";
    }

    @Override
    public void start(ChannelMessageHandler handler) {
        this.handler = handler;

        if (config.useGateway()) {
            startGateway();
            log.info("Discord adapter started in GATEWAY mode (no public endpoint needed)");
        } else {
            webhookDispatcher.register("discord", this::handleWebhook);
            log.info("Discord adapter started in WEBHOOK mode (Interactions)");
        }

        running.set(true);
    }

    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        try {
            String url = DISCORD_API_BASE + "channels/" + message.peerId() + "/messages";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bot " + config.botToken());

            Map<String, Object> body = Map.of("content", message.content());

            var request = new HttpEntity<>(body, headers);
            var response = restTemplate.postForEntity(url, request, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String messageId = response.getBody().path("id").asText();
                return new DeliveryResult.Success(messageId);
            } else {
                return new DeliveryResult.Failure(
                        "discord_api_error",
                        "HTTP " + response.getStatusCode(),
                        true);
            }
        } catch (Exception e) {
            log.error("Failed to send Discord message to channel {}", message.peerId(), e);
            return new DeliveryResult.Failure("send_failed", e.getMessage(), true);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
        WebSocket ws = gatewayWs.getAndSet(null);
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutting down");
        }
        if (gatewayReconnectThread != null) {
            gatewayReconnectThread.interrupt();
        }
        log.info("Discord adapter stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // --- Gateway WebSocket mode ---

    private void startGateway() {
        gatewayReconnectThread = Thread.ofVirtual().name("discord-gateway").start(() -> {
            while (running.get() || !Thread.currentThread().isInterrupted()) {
                try {
                    connectGateway();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("Discord Gateway error (will reconnect): {}", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("Discord Gateway reconnect loop stopped");
        });
    }

    /** Fetches the Gateway URL and connects via WebSocket. */
    void connectGateway() throws Exception {
        String gatewayUrl = fetchGatewayUrl();
        if (gatewayUrl == null) {
            throw new IllegalStateException("Failed to obtain Discord Gateway URL");
        }

        String wsUrl = gatewayUrl + "?v=" + GATEWAY_VERSION + "&encoding=json";
        log.info("Connecting to Discord Gateway...");

        var latch = new java.util.concurrent.CountDownLatch(1);

        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {

                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        log.info("Discord Gateway WebSocket connected");
                        gatewayWs.set(webSocket);
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            String message = buffer.toString();
                            buffer.setLength(0);
                            handleGatewayMessage(message, webSocket);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        log.info("Discord Gateway disconnected: {} {}", statusCode, reason);
                        gatewayWs.set(null);
                        latch.countDown();
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        log.warn("Discord Gateway WebSocket error: {}", error.getMessage());
                        gatewayWs.set(null);
                        latch.countDown();
                    }
                }).join();

        // Block until disconnected, then the reconnect loop retries
        latch.await();
    }

    /** Calls GET /gateway/bot to obtain the WebSocket URL. */
    String fetchGatewayUrl() {
        try {
            String url = DISCORD_API_BASE + "gateway/bot";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bot " + config.botToken());

            var request = new HttpEntity<>(headers);
            var response = restTemplate.exchange(url, HttpMethod.GET, request, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().path("url").asText();
            }
        } catch (Exception e) {
            log.error("Failed to fetch Discord Gateway URL: {}", e.getMessage());
        }
        return null;
    }

    /** Processes a Gateway dispatch message. */
    void handleGatewayMessage(String message, WebSocket ws) {
        try {
            JsonNode payload = MAPPER.readTree(message);
            int op = payload.path("op").asInt();

            // Update sequence number
            if (!payload.path("s").isNull()) {
                lastSequence.set(payload.path("s").asInt());
            }

            switch (op) {
                case 10 -> handleHello(payload, ws);      // HELLO
                case 11 -> { }                              // HEARTBEAT_ACK
                case 0 -> handleDispatch(payload);          // DISPATCH
                case 7 -> {                                 // RECONNECT
                    log.info("Discord Gateway requested reconnect");
                    ws.sendClose(4000, "reconnecting");
                }
                case 9 -> {                                 // INVALID_SESSION
                    log.info("Discord Gateway invalid session, re-identifying");
                    sessionId.set(null);
                    sendIdentify(ws);
                }
                default -> log.debug("Discord Gateway op={}", op);
            }
        } catch (Exception e) {
            log.error("Failed to process Discord Gateway message", e);
        }
    }

    /** Handles HELLO (op 10): starts heartbeat and sends IDENTIFY. */
    private void handleHello(JsonNode payload, WebSocket ws) {
        long heartbeatInterval = payload.path("d").path("heartbeat_interval").asLong();
        log.debug("Discord Gateway HELLO, heartbeat interval={}ms", heartbeatInterval);

        startHeartbeat(ws, heartbeatInterval);
        sendIdentify(ws);
    }

    /** Sends IDENTIFY (op 2) with bot token and intents. */
    private void sendIdentify(WebSocket ws) {
        try {
            // Intents: GUILDS (1) | GUILD_MESSAGES (512) | MESSAGE_CONTENT (32768) | DIRECT_MESSAGES (4096)
            int intents = 1 | 512 | 4096 | 32768;

            String identify = MAPPER.writeValueAsString(Map.of(
                    "op", 2,
                    "d", Map.of(
                            "token", config.botToken(),
                            "intents", intents,
                            "properties", Map.of(
                                    "os", "linux",
                                    "browser", "jclaw",
                                    "device", "jclaw"
                            )
                    )
            ));
            ws.sendText(identify, true);
            log.debug("Discord Gateway IDENTIFY sent");
        } catch (Exception e) {
            log.error("Failed to send Discord IDENTIFY", e);
        }
    }

    /** Starts the heartbeat scheduler. */
    private void startHeartbeat(WebSocket ws, long intervalMs) {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "discord-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                Integer seq = lastSequence.get();
                String heartbeat = seq != null
                        ? "{\"op\":1,\"d\":" + seq + "}"
                        : "{\"op\":1,\"d\":null}";
                ws.sendText(heartbeat, true);
            } catch (Exception e) {
                log.warn("Failed to send Discord heartbeat: {}", e.getMessage());
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    /** Handles DISPATCH (op 0) events. */
    private void handleDispatch(JsonNode payload) {
        String eventName = payload.path("t").asText();

        if ("READY".equals(eventName)) {
            sessionId.set(payload.path("d").path("session_id").asText());
            log.info("Discord Gateway READY, session={}", sessionId.get());
            return;
        }

        if ("MESSAGE_CREATE".equals(eventName)) {
            processMessageCreate(payload.path("d"));
        }
    }

    /** Processes a MESSAGE_CREATE event into a ChannelMessage. */
    private void processMessageCreate(JsonNode data) {
        // Ignore bot messages
        JsonNode author = data.path("author");
        if (author.path("bot").asBoolean(false)) {
            return;
        }

        String authorId = author.path("id").asText();
        if (!config.isSenderAllowed(authorId)) {
            log.debug("Dropping message from non-allowed Discord user {}", authorId);
            return;
        }

        String content = data.path("content").asText("");
        if (content.isBlank()) {
            return;
        }

        String channelIdValue = data.path("channel_id").asText();
        String guildId = data.path("guild_id").asText("");
        String messageId = data.path("id").asText();

        Map<String, Object> platformData = Map.of(
                "guild_id", guildId,
                "channel_id", channelIdValue,
                "message_id", messageId,
                "author_id", author.path("id").asText()
        );

        var channelMessage = ChannelMessage.inbound(
                messageId, "discord", guildId, channelIdValue, content, platformData);

        if (handler != null) {
            handler.onMessage(channelMessage);
        }
    }

    // --- Webhook mode (Interactions) ---

    private ResponseEntity<String> handleWebhook(String body, Map<String, String> headers) {
        try {
            JsonNode payload = MAPPER.readTree(body);
            int type = payload.path("type").asInt();

            // Type 1: PING — Discord verification
            if (type == 1) {
                return ResponseEntity.ok("{\"type\": 1}");
            }

            // Type 2: APPLICATION_COMMAND or Type 4: MESSAGE_COMPONENT
            if (payload.has("data")) {
                JsonNode data = payload.path("data");
                String content = data.has("content") ? data.path("content").asText() : "";
                String channelIdValue = payload.path("channel_id").asText();
                String guildId = payload.path("guild_id").asText();
                String interactionId = payload.path("id").asText();

                if (!content.isBlank()) {
                    Map<String, Object> platformData = Map.of(
                            "guild_id", guildId,
                            "channel_id", channelIdValue,
                            "interaction_id", interactionId,
                            "type", type
                    );

                    var channelMessage = ChannelMessage.inbound(
                            interactionId, "discord", guildId, channelIdValue, content, platformData);

                    if (handler != null) {
                        handler.onMessage(channelMessage);
                    }
                }
            }

            return ResponseEntity.ok("");
        } catch (Exception e) {
            log.error("Failed to process Discord webhook", e);
            return ResponseEntity.ok("");
        }
    }
}
