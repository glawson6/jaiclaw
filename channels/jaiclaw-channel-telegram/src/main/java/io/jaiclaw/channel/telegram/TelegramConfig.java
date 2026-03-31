package io.jaiclaw.channel.telegram;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Configuration for the Telegram channel adapter.
 *
 * <p>Two inbound modes:
 * <ul>
 *   <li><b>Polling</b> (default for local dev): calls getUpdates in a loop. No public endpoint needed.</li>
 *   <li><b>Webhook</b> (production): Telegram POSTs updates to webhookUrl.</li>
 * </ul>
 *
 * <p>If {@code webhookUrl} is blank, the adapter uses polling mode automatically.
 *
 * <p>If {@code allowedUserIds} is non-empty, only messages from those Telegram user IDs
 * are processed; all others are silently dropped. An empty set means allow everyone.
 */
public record TelegramConfig(
        String botToken,
        String webhookUrl,
        boolean enabled,
        int pollingTimeoutSeconds,
        Set<String> allowedUserIds,
        boolean verifyWebhook,
        String webhookSecretToken,
        boolean maskBotToken
) {
    public TelegramConfig {
        if (botToken == null) botToken = "";
        if (webhookUrl == null) webhookUrl = "";
        if (pollingTimeoutSeconds <= 0) pollingTimeoutSeconds = 30;
        if (allowedUserIds == null) allowedUserIds = Set.of();
        if (webhookSecretToken == null) webhookSecretToken = "";
    }

    public TelegramConfig(String botToken, String webhookUrl, boolean enabled) {
        this(botToken, webhookUrl, enabled, 30, Set.of(), false, "", false);
    }

    public TelegramConfig(String botToken, String webhookUrl, boolean enabled, int pollingTimeoutSeconds) {
        this(botToken, webhookUrl, enabled, pollingTimeoutSeconds, Set.of(), false, "", false);
    }

    public TelegramConfig(String botToken, String webhookUrl, boolean enabled,
                           int pollingTimeoutSeconds, Set<String> allowedUserIds) {
        this(botToken, webhookUrl, enabled, pollingTimeoutSeconds, allowedUserIds, false, "", false);
    }

    /**
     * Returns true if the given user ID is allowed to interact with the bot.
     * An empty allowedUserIds set means all users are allowed.
     */
    public boolean isUserAllowed(String userId) {
        return allowedUserIds.isEmpty() || allowedUserIds.contains(userId);
    }

    public boolean usePolling() {
        return webhookUrl.isBlank();
    }

    /**
     * Returns a masked account ID for use in session keys when {@code maskBotToken} is enabled.
     * Uses the first 12 hex chars of the SHA-256 hash of the bot token.
     * When masking is disabled, returns the raw bot token (backward-compatible).
     */
    public String accountId() {
        if (!maskBotToken || botToken.isBlank()) {
            return botToken;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(botToken.getBytes(StandardCharsets.UTF_8));
            return "tg_" + HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in JDK
            return botToken;
        }
    }

    public static final TelegramConfig DISABLED = new TelegramConfig("", "", false);

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String botToken;
        private String webhookUrl;
        private boolean enabled;
        private int pollingTimeoutSeconds;
        private Set<String> allowedUserIds;
        private boolean verifyWebhook;
        private String webhookSecretToken;
        private boolean maskBotToken;

        public Builder botToken(String botToken) { this.botToken = botToken; return this; }
        public Builder webhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder pollingTimeoutSeconds(int pollingTimeoutSeconds) { this.pollingTimeoutSeconds = pollingTimeoutSeconds; return this; }
        public Builder allowedUserIds(Set<String> allowedUserIds) { this.allowedUserIds = allowedUserIds; return this; }
        public Builder verifyWebhook(boolean verifyWebhook) { this.verifyWebhook = verifyWebhook; return this; }
        public Builder webhookSecretToken(String webhookSecretToken) { this.webhookSecretToken = webhookSecretToken; return this; }
        public Builder maskBotToken(boolean maskBotToken) { this.maskBotToken = maskBotToken; return this; }

        public TelegramConfig build() {
            return new TelegramConfig(
                    botToken, webhookUrl, enabled, pollingTimeoutSeconds, allowedUserIds,
                    verifyWebhook, webhookSecretToken, maskBotToken);
        }
    }
}
