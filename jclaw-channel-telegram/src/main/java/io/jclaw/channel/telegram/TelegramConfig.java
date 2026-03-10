package io.jclaw.channel.telegram;

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
 */
public record TelegramConfig(
        String botToken,
        String webhookUrl,
        boolean enabled,
        int pollingTimeoutSeconds
) {
    public TelegramConfig {
        if (botToken == null) botToken = "";
        if (webhookUrl == null) webhookUrl = "";
        if (pollingTimeoutSeconds <= 0) pollingTimeoutSeconds = 30;
    }

    public TelegramConfig(String botToken, String webhookUrl, boolean enabled) {
        this(botToken, webhookUrl, enabled, 30);
    }

    public boolean usePolling() {
        return webhookUrl.isBlank();
    }

    public static final TelegramConfig DISABLED = new TelegramConfig("", "", false);
}
