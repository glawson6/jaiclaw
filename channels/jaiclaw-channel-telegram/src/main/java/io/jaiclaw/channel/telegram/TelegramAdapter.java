package io.jaiclaw.channel.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.channel.*;
import io.jaiclaw.channel.chunking.PlatformLimits;
import io.jaiclaw.gateway.WebhookDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Telegram Bot API channel adapter with two inbound modes:
 *
 * <p><b>Polling mode</b> (local dev): Delegates to a {@link TelegramPollingStrategy}
 * to fetch updates. No public endpoint needed. Activated when {@code webhookUrl} is blank.
 *
 * <p><b>Webhook mode</b> (production): Receives updates via POST /webhook/telegram.
 * Activated when {@code webhookUrl} is set.
 *
 * <p>Outbound: Always uses Telegram Bot API sendMessage.
 *
 * <p>HTTP transport is delegated to a {@link TelegramHttpClient} implementation,
 * selectable via {@code jaiclaw.channels.telegram.http-client} (jdk | rest-template | web-client).
 *
 * <p>Polling strategy is delegated to a {@link TelegramPollingStrategy} implementation,
 * selectable via {@code jaiclaw.channels.telegram.polling-strategy} (native | camel).
 */
public class TelegramAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(TelegramAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";

    private final TelegramConfig config;
    private final WebhookDispatcher webhookDispatcher;
    private final TelegramHttpClient httpClient;
    private final TelegramPollingStrategy pollingStrategy;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ChannelMessageHandler handler;

    public TelegramAdapter(TelegramConfig config, WebhookDispatcher webhookDispatcher) {
        this(config, webhookDispatcher,
                new JdkHttpClientTelegramHttpClient(config.pollingTimeoutSeconds()),
                new NativeTelegramPollingStrategy(
                        new JdkHttpClientTelegramHttpClient(config.pollingTimeoutSeconds())));
    }

    public TelegramAdapter(TelegramConfig config, WebhookDispatcher webhookDispatcher,
                           TelegramHttpClient httpClient) {
        this(config, webhookDispatcher, httpClient,
                new NativeTelegramPollingStrategy(httpClient));
    }

    public TelegramAdapter(TelegramConfig config, WebhookDispatcher webhookDispatcher,
                           TelegramHttpClient httpClient,
                           TelegramPollingStrategy pollingStrategy) {
        this.config = config;
        this.webhookDispatcher = webhookDispatcher;
        this.httpClient = httpClient;
        this.pollingStrategy = pollingStrategy;
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
    public PlatformLimits platformLimits() {
        return PlatformLimits.TELEGRAM;
    }

    @Override
    public void start(ChannelMessageHandler handler) {
        this.handler = handler;
        running.set(true);

        if (config.usePolling()) {
            pollingStrategy.startPolling(config, this::processUpdate);
            log.info("Telegram adapter started in POLLING mode ({} strategy)",
                    config.pollingStrategyType());
        } else {
            webhookDispatcher.register("telegram", this::handleWebhook);
            registerWebhook();
            log.info("Telegram adapter started in WEBHOOK mode: {}", config.webhookUrl());
        }
    }

    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        try {
            // Send file attachments first
            if (message.hasAttachments()) {
                for (var attachment : message.attachments()) {
                    if (attachment.data() != null && attachment.data().length > 0) {
                        sendDocument(message.peerId(), attachment.data(),
                                attachment.name(), null);
                    }
                }
            }

            // Send text content (skip if empty and we already sent attachments)
            if (message.content() != null && !message.content().isBlank()) {
                return sendText(message.peerId(), message.content());
            }

            // If we only sent attachments, return success
            if (message.hasAttachments()) {
                return new DeliveryResult.Success("attachments_sent");
            }

            return new DeliveryResult.Failure("empty_message", "No content or attachments", false);
        } catch (Exception e) {
            log.error("Failed to send Telegram message to {}", message.peerId(), e);
            return new DeliveryResult.Failure("send_failed", e.getMessage(), true);
        }
    }

    /**
     * Send a text message to a Telegram chat.
     * Attempts Markdown parse mode first; falls back to plain text if Telegram rejects the entities.
     */
    DeliveryResult sendText(String chatId, String text) {
        String url = TELEGRAM_API_BASE + config.botToken() + "/sendMessage";

        // Try with Markdown first
        try {
            Map<String, Object> body = Map.of(
                    "chat_id", chatId,
                    "text", text,
                    "parse_mode", "Markdown"
            );
            JsonNode response = httpClient.post(url, body);
            String messageId = response.path("result").path("message_id").asText();
            return new DeliveryResult.Success(messageId);
        } catch (Exception markdownEx) {
            String msg = markdownEx.getMessage();
            if (msg != null && msg.contains("can't parse entities")) {
                log.warn("Markdown parse failed for chat {}, retrying as plain text", chatId);
                // Retry without parse_mode
                Map<String, Object> fallbackBody = Map.of(
                        "chat_id", chatId,
                        "text", text
                );
                JsonNode response = httpClient.post(url, fallbackBody);
                String messageId = response.path("result").path("message_id").asText();
                return new DeliveryResult.Success(messageId);
            }
            throw markdownEx;
        }
    }

    /**
     * Send a document (file) to a Telegram chat via multipart/form-data.
     */
    DeliveryResult sendDocument(String chatId, byte[] fileData, String filename, String caption) {
        try {
            String url = TELEGRAM_API_BASE + config.botToken() + "/sendDocument";

            Map<String, Object> parts = new LinkedHashMap<>();
            parts.put("chat_id", chatId);
            parts.put("document", new MultipartFile(filename, fileData));
            if (caption != null && !caption.isBlank()) {
                parts.put("caption", caption);
            }

            JsonNode response = httpClient.postMultipart(url, parts);
            String messageId = response.path("result").path("message_id").asText();
            log.debug("Sent document '{}' to chat {}, messageId={}", filename, chatId, messageId);
            return new DeliveryResult.Success(messageId);
        } catch (Exception e) {
            log.error("Failed to send document '{}' to chat {}", filename, chatId, e);
            return new DeliveryResult.Failure("send_document_failed", e.getMessage(), true);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (config.usePolling()) {
            pollingStrategy.stopPolling();
        }
        log.info("Telegram adapter stopped");
    }

    @Override
    public boolean isRunning() {
        if (config.usePolling()) {
            return running.get() && pollingStrategy.isPolling();
        }
        return running.get();
    }

    // --- Update processing (shared by both polling and webhook) ---

    void processUpdate(JsonNode update) {
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
                updateId, "telegram", config.accountId(), chatId, text, attachments, platformData);

        if (handler != null) {
            try {
                handler.onMessage(channelMessage);
            } catch (Exception e) {
                log.error("Error processing Telegram message from user {}: {}",
                        fromId, e.getMessage(), e);
            }
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
            if (!fileId.isBlank()) {
                String fileName = doc.has("file_name") ? doc.path("file_name").asText() : "document";
                String mimeType = doc.has("mime_type") ? doc.path("mime_type").asText() : "application/octet-stream";
                byte[] data = downloadFile(fileId);
                if (data != null) {
                    attachments.add(new ChannelMessage.Attachment(fileName, mimeType, null, data));
                }
            }
        }

        // Photo — Telegram sends multiple sizes; pick the largest (last in array)
        if (messageNode.has("photo") && messageNode.path("photo").isArray()) {
            JsonNode photos = messageNode.path("photo");
            if (photos.size() > 0) {
                JsonNode largest = photos.get(photos.size() - 1);
                String fileId = largest.path("file_id").asText();
                if (!fileId.isBlank()) {
                    byte[] data = downloadFile(fileId);
                    if (data != null) {
                        attachments.add(new ChannelMessage.Attachment("photo.jpg", "image/jpeg", null, data));
                    }
                }
            }
        }

        // Video
        if (messageNode.has("video")) {
            JsonNode video = messageNode.path("video");
            String fileId = video.path("file_id").asText();
            if (!fileId.isBlank()) {
                String mimeType = video.has("mime_type") ? video.path("mime_type").asText() : "video/mp4";
                byte[] data = downloadFile(fileId);
                if (data != null) {
                    attachments.add(new ChannelMessage.Attachment("video.mp4", mimeType, null, data));
                }
            }
        }

        // Audio
        if (messageNode.has("audio")) {
            JsonNode audio = messageNode.path("audio");
            String fileId = audio.path("file_id").asText();
            if (!fileId.isBlank()) {
                String fileName = audio.has("file_name") ? audio.path("file_name").asText() : "audio.mp3";
                String mimeType = audio.has("mime_type") ? audio.path("mime_type").asText() : "audio/mpeg";
                byte[] data = downloadFile(fileId);
                if (data != null) {
                    attachments.add(new ChannelMessage.Attachment(fileName, mimeType, null, data));
                }
            }
        }

        // Voice message
        if (messageNode.has("voice")) {
            JsonNode voice = messageNode.path("voice");
            String fileId = voice.path("file_id").asText();
            if (!fileId.isBlank()) {
                String mimeType = voice.has("mime_type") ? voice.path("mime_type").asText() : "audio/ogg";
                byte[] data = downloadFile(fileId);
                if (data != null) {
                    attachments.add(new ChannelMessage.Attachment("voice.ogg", mimeType, null, data));
                }
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
            JsonNode fileResponse = httpClient.get(getFileUrl);

            String filePath = fileResponse.path("result").path("file_path").asText();
            if (filePath.isEmpty()) {
                log.warn("Empty file_path for fileId={}", fileId);
                return null;
            }

            // Step 2: Download the file bytes
            String downloadUrl = "https://api.telegram.org/file/bot" + config.botToken() + "/" + filePath;
            return httpClient.getBytes(downloadUrl);
        } catch (Exception e) {
            log.warn("Failed to download Telegram file fileId={}: {}", fileId, e.getMessage());
            return null;
        }
    }

    // --- Webhook mode ---

    private void registerWebhook() {
        try {
            String url = TELEGRAM_API_BASE + config.botToken() + "/setWebhook";
            Map<String, Object> body = new HashMap<>();
            body.put("url", config.webhookUrl());
            body.put("allowed_updates", new String[]{"message"});

            // Include secret_token if webhook verification is enabled
            if (config.verifyWebhook() && !config.webhookSecretToken().isBlank()) {
                body.put("secret_token", config.webhookSecretToken());
            }

            httpClient.post(url, body);
            log.info("Registered Telegram webhook: {}", config.webhookUrl());
        } catch (Exception e) {
            log.warn("Failed to register Telegram webhook: {}", e.getMessage());
        }
    }

    private ResponseEntity<String> handleWebhook(String body, Map<String, String> headers) {
        try {
            // Verify webhook secret token when verifyWebhook is enabled
            if (config.verifyWebhook() && !config.webhookSecretToken().isBlank()) {
                String provided = headers.get("x-telegram-bot-api-secret-token");
                if (provided == null || !MessageDigest.isEqual(
                        provided.getBytes(StandardCharsets.UTF_8),
                        config.webhookSecretToken().getBytes(StandardCharsets.UTF_8))) {
                    log.warn("Telegram webhook secret token verification failed");
                    return ResponseEntity.status(401).body("invalid secret token");
                }
            }

            JsonNode update = MAPPER.readTree(body);
            processUpdate(update);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Failed to process Telegram webhook", e);
            return ResponseEntity.ok("ok"); // Always return 200 to Telegram
        }
    }
}
