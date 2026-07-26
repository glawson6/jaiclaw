package io.jaiclaw.channel.googlechat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.channel.*;
import io.jaiclaw.channel.chunking.PlatformLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Google Chat messaging channel adapter.
 *
 * <p>Inbound messages arrive via HTTP webhook (Pub/Sub push), processed by
 * {@link #processWebhookPayload(String)}. Outbound messages are sent via
 * the Google Chat REST API using service account authentication.
 */
public class GoogleChatAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(GoogleChatAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CHAT_API_BASE = "https://chat.googleapis.com/v1";

    private final GoogleChatConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ChannelMessageHandler handler;
    private HttpClient httpClient;

    public GoogleChatAdapter(GoogleChatConfig config) {
        this.config = config;
    }

    // Visible for testing
    GoogleChatAdapter(GoogleChatConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    @Override
    public String channelId() {
        return "googlechat";
    }

    @Override
    public String displayName() {
        return "Google Chat";
    }

    @Override
    public PlatformLimits platformLimits() {
        return PlatformLimits.GOOGLE_CHAT;
    }

    @Override
    public void start(ChannelMessageHandler handler) {
        this.handler = handler;
        if (httpClient == null) {
            httpClient = HttpClient.newHttpClient();
        }
        running.set(true);
        log.info("Google Chat adapter started (webhook={})", config.webhookPath());
    }

    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        try {
            String spaceName = message.accountId();
            if (spaceName == null || spaceName.isEmpty()) {
                return new DeliveryResult.Failure("no_space", "No space name in message", false);
            }

            String json = MAPPER.writeValueAsString(Map.of(
                    "text", message.content()
            ));

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(CHAT_API_BASE + "/" + spaceName + "/messages"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode responseBody = MAPPER.readTree(response.body());
                String msgName = responseBody.path("name").asText(UUID.randomUUID().toString());
                return new DeliveryResult.Success(msgName);
            } else {
                return new DeliveryResult.Failure(
                        "googlechat_api_error",
                        "HTTP " + response.statusCode(),
                        response.statusCode() >= 500);
            }
        } catch (Exception e) {
            log.error("Failed to send Google Chat message to {}", message.accountId(), e);
            return new DeliveryResult.Failure("send_failed", e.getMessage(), true);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        log.info("Google Chat adapter stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Process a Google Chat webhook payload from Pub/Sub push.
     *
     * @param body the raw request body
     */
    public void processWebhookPayload(String body) {
        if (body == null || body.isEmpty()) return;

        try {
            JsonNode event = MAPPER.readTree(body);
            processEvent(event);
        } catch (Exception e) {
            log.warn("Failed to process Google Chat webhook payload: {}", e.getMessage());
        }
    }

    void processEvent(JsonNode event) {
        String senderName = event.path("user").path("name").asText("");
        if (senderName.isEmpty()) return;

        if (!config.isSenderAllowed(senderName)) {
            log.debug("Dropping message from non-allowed Google Chat sender {}", senderName);
            return;
        }

        ChannelMessage channelMessage = GoogleChatMessageMapper.mapEvent(event);
        if (channelMessage != null && handler != null) {
            handler.onMessage(channelMessage);
        }
    }

    public GoogleChatConfig config() {
        return config;
    }
}
