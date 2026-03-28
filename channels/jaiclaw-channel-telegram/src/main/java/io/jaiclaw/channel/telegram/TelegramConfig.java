package io.jaiclaw.channel.telegram;

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
        Set<String> allowedUserIds
) {
    public TelegramConfig {
        if (botToken == null) botToken = "";
        if (webhookUrl == null) webhookUrl = "";
        if (pollingTimeoutSeconds <= 0) pollingTimeoutSeconds = 30;
        if (allowedUserIds == null) allowedUserIds = Set.of();
    }

    public TelegramConfig(String botToken, String webhookUrl, boolean enabled) {
        this(botToken, webhookUrl, enabled, 30, Set.of());
    }

    public TelegramConfig(String botToken, String webhookUrl, boolean enabled, int pollingTimeoutSeconds) {
        this(botToken, webhookUrl, enabled, pollingTimeoutSeconds, Set.of());
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

    public static final TelegramConfig DISABLED = new TelegramConfig("", "", false);

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String botToken;
        private String webhookUrl;
        private boolean enabled;
        private int pollingTimeoutSeconds;
        private Set<String> allowedUserIds;

        public Builder botToken(String botToken) { this.botToken = botToken; return this; }
        public Builder webhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder pollingTimeoutSeconds(int pollingTimeoutSeconds) { this.pollingTimeoutSeconds = pollingTimeoutSeconds; return this; }
        public Builder allowedUserIds(Set<String> allowedUserIds) { this.allowedUserIds = allowedUserIds; return this; }

        public TelegramConfig build() {
            return new TelegramConfig(
                    botToken, webhookUrl, enabled, pollingTimeoutSeconds, allowedUserIds);
        }
    }
}
