package io.jclaw.channel.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jclaw.channel.*;
import io.jclaw.gateway.WebhookDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Telegram Bot API channel adapter with two inbound modes:
 *
 * <p><b>Polling mode</b> (local dev): Calls getUpdates in a loop on a virtual thread.
 * No public endpoint needed. Activated when {@code webhookUrl} is blank.
 *
 * <p><b>Webhook mode</b> (production): Receives updates via POST /webhook/telegram.
 * Activated when {@code webhookUrl} is set.
 *
 * <p>Outbound: Always uses Telegram Bot API sendMessage.
 */
public class TelegramAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(TelegramAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";

    private final TelegramConfig config;
    private final WebhookDispatcher webhookDispatcher;
    private final RestTemplate restTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong pollingOffset = new AtomicLong(0);
    private ChannelMessageHandler handler;
    private Thread pollingThread;

    public TelegramAdapter(TelegramConfig config, WebhookDispatcher webhookDispatcher) {
        this(config, webhookDispatcher, new RestTemplate());
    }

    public TelegramAdapter(TelegramConfig config, WebhookDispatcher webhookDispatcher,
                           RestTemplate restTemplate) {
        this.config = config;
        this.webhookDispatcher = webhookDispatcher;
        this.restTemplate = restTemplate;
    }

    @Override
    public String channelId() {
        return "telegram";
    }

    @Override
    public String displayName() {
        return "Telegram";
    }

    @Override
    public void start(ChannelMessageHandler handler) {
        this.handler = handler;

        if (config.usePolling()) {
            startPolling();
            log.info("Telegram adapter started in POLLING mode (no public endpoint needed)");
        } else {
            webhookDispatcher.register("telegram", this::handleWebhook);
            registerWebhook();
            log.info("Telegram adapter started in WEBHOOK mode: {}", config.webhookUrl());
        }

        running.set(true);
    }

    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        try {
            String url = TELEGRAM_API_BASE + config.botToken() + "/sendMessage";

            Map<String, Object> body = Map.of(
                    "chat_id", message.peerId(),
                    "text", message.content(),
                    "parse_mode", "Markdown"
            );

            var response = restTemplate.postForEntity(url, body, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String messageId = response.getBody().path("result").path("message_id").asText();
                return new DeliveryResult.Success(messageId);
            } else {
                return new DeliveryResult.Failure(
                        "telegram_api_error",
                        "HTTP " + response.getStatusCode(),
                        true);
            }
        } catch (Exception e) {
            log.error("Failed to send Telegram message to {}", message.peerId(), e);
            return new DeliveryResult.Failure("send_failed", e.getMessage(), true);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
        log.info("Telegram adapter stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // --- Polling mode ---

    private void startPolling() {
        // Delete any existing webhook so polling works
        deleteWebhook();

        pollingThread = Thread.ofVirtual().name("telegram-poller").start(() -> {
            log.info("Telegram polling started (timeout={}s)", config.pollingTimeoutSeconds());
            while (running.get() || !Thread.currentThread().isInterrupted()) {
                try {
                    pollUpdates();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("Telegram polling error (will retry): {}", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("Telegram polling stopped");
        });
    }

    private void pollUpdates() throws InterruptedException {
        String url = TELEGRAM_API_BASE + config.botToken() + "/getUpdates"
                + "?offset=" + pollingOffset.get()
                + "&timeout=" + config.pollingTimeoutSeconds()
                + "&allowed_updates=%5B%22message%22%5D";

        try {
            var response = restTemplate.getForEntity(url, JsonNode.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return;
            }

            JsonNode result = response.getBody().path("result");
            if (!result.isArray()) return;

            for (JsonNode update : result) {
                long updateId = update.path("update_id").asLong();
                pollingOffset.set(updateId + 1);
                processUpdate(update);
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Timeout or connection issue — normal during long polling
            if (e.getCause() instanceof java.net.SocketTimeoutException) {
                return; // Normal — no updates within timeout period
            }
            throw e;
        }
    }

    private void processUpdate(JsonNode update) {
        JsonNode messageNode = update.path("message");
        if (messageNode.isMissingNode()) return;

        // Check allowed-users filter
        String fromId = String.valueOf(messageNode.path("from").path("id").asLong());
        if (!config.isUserAllowed(fromId)) {
            log.debug("Dropping message from non-allowed Telegram user {}", fromId);
            return;
        }

        String chatId = String.valueOf(messageNode.path("chat").path("id").asLong());
        String updateId = String.valueOf(update.path("update_id").asLong());

        Map<String, Object> platformData = Map.of(
                "update_id", update.path("update_id").asLong(),
                "chat_id", messageNode.path("chat").path("id").asLong(),
                "message_id", messageNode.path("message_id").asLong()
        );

        // Extract text (may accompany a document as caption)
        String text = messageNode.has("text") ? messageNode.path("text").asText()
                : messageNode.has("caption") ? messageNode.path("caption").asText("")
                : "";

        // Extract file attachments (document, photo, video, audio, voice)
        List<ChannelMessage.Attachment> attachments = extractAttachments(messageNode);

        // Skip if no text and no attachments
        if (text.isEmpty() && attachments.isEmpty()) return;

        var channelMessage = ChannelMessage.inbound(
                updateId, "telegram", config.botToken(), chatId, text, attachments, platformData);

        if (handler != null) {
            handler.onMessage(channelMessage);
        }
    }

    /**
     * Extract file attachments from a Telegram message node.
     * Supports document, photo, video, audio, and voice message types.
     */
    List<ChannelMessage.Attachment> extractAttachments(JsonNode messageNode) {
        List<ChannelMessage.Attachment> attachments = new java.util.ArrayList<>();

        // Document (PDF, DOCX, etc.)
        if (messageNode.has("document")) {
            JsonNode doc = messageNode.path("document");
            String fileId = doc.path("file_id").asText();
            String fileName = doc.has("file_name") ? doc.path("file_name").asText() : "document";
            String mimeType = doc.has("mime_type") ? doc.path("mime_type").asText() : "application/octet-stream";
            byte[] data = downloadFile(fileId);
            if (data != null) {
                attachments.add(new ChannelMessage.Attachment(fileName, mimeType, null, data));
            }
        }

        // Photo — Telegram sends multiple sizes; pick the largest (last in array)
        if (messageNode.has("photo") && messageNode.path("photo").isArray()) {
            JsonNode photos = messageNode.path("photo");
            JsonNode largest = photos.get(photos.size() - 1);
            String fileId = largest.path("file_id").asText();
            byte[] data = downloadFile(fileId);
            if (data != null) {
                attachments.add(new ChannelMessage.Attachment("photo.jpg", "image/jpeg", null, data));
            }
        }

        // Video
        if (messageNode.has("video")) {
            JsonNode video = messageNode.path("video");
            String fileId = video.path("file_id").asText();
            String mimeType = video.has("mime_type") ? video.path("mime_type").asText() : "video/mp4";
            byte[] data = downloadFile(fileId);
            if (data != null) {
                attachments.add(new ChannelMessage.Attachment("video.mp4", mimeType, null, data));
            }
        }

        // Audio
        if (messageNode.has("audio")) {
            JsonNode audio = messageNode.path("audio");
            String fileId = audio.path("file_id").asText();
            String fileName = audio.has("file_name") ? audio.path("file_name").asText() : "audio.mp3";
            String mimeType = audio.has("mime_type") ? audio.path("mime_type").asText() : "audio/mpeg";
            byte[] data = downloadFile(fileId);
            if (data != null) {
                attachments.add(new ChannelMessage.Attachment(fileName, mimeType, null, data));
            }
        }

        // Voice message
        if (messageNode.has("voice")) {
            JsonNode voice = messageNode.path("voice");
            String fileId = voice.path("file_id").asText();
            String mimeType = voice.has("mime_type") ? voice.path("mime_type").asText() : "audio/ogg";
            byte[] data = downloadFile(fileId);
            if (data != null) {
                attachments.add(new ChannelMessage.Attachment("voice.ogg", mimeType, null, data));
            }
        }

        return attachments.isEmpty() ? List.of() : List.copyOf(attachments);
    }

    /**
     * Download a file from Telegram's servers using the Bot API getFile + file download.
     * Returns null if download fails.
     */
    byte[] downloadFile(String fileId) {
        try {
            // Step 1: Get file path via getFile API
            String getFileUrl = TELEGRAM_API_BASE + config.botToken() + "/getFile?file_id=" + fileId;
            var fileResponse = restTemplate.getForEntity(getFileUrl, JsonNode.class);

            if (!fileResponse.getStatusCode().is2xxSuccessful() || fileResponse.getBody() == null) {
                log.warn("Failed to get file info for fileId={}", fileId);
                return null;
            }

            String filePath = fileResponse.getBody().path("result").path("file_path").asText();
            if (filePath.isEmpty()) {
                log.warn("Empty file_path for fileId={}", fileId);
                return null;
            }

            // Step 2: Download the file bytes
            String downloadUrl = "https://api.telegram.org/file/bot" + config.botToken() + "/" + filePath;
            byte[] bytes = restTemplate.getForObject(downloadUrl, byte[].class);
            return bytes;
        } catch (Exception e) {
            log.warn("Failed to download Telegram file fileId={}: {}", fileId, e.getMessage());
            return null;
        }
    }

    private void deleteWebhook() {
        try {
            String url = TELEGRAM_API_BASE + config.botToken() + "/deleteWebhook";
            restTemplate.getForEntity(url, String.class);
            log.debug("Deleted existing Telegram webhook (switching to polling)");
        } catch (Exception e) {
            log.warn("Failed to delete Telegram webhook: {}", e.getMessage());
        }
    }

    // --- Webhook mode ---

    private void registerWebhook() {
        try {
            String url = TELEGRAM_API_BASE + config.botToken() + "/setWebhook";
            Map<String, Object> body = Map.of(
                    "url", config.webhookUrl(),
                    "allowed_updates", new String[]{"message"}
            );
            restTemplate.postForEntity(url, body, String.class);
            log.info("Registered Telegram webhook: {}", config.webhookUrl());
        } catch (Exception e) {
            log.warn("Failed to register Telegram webhook: {}", e.getMessage());
        }
    }

    private ResponseEntity<String> handleWebhook(String body, Map<String, String> headers) {
        try {
            JsonNode update = MAPPER.readTree(body);
            processUpdate(update);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Failed to process Telegram webhook", e);
            return ResponseEntity.ok("ok"); // Always return 200 to Telegram
        }
    }
}
