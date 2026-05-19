package io.jaiclaw.channel.telegram;

/**
 * Selects which {@link TelegramPollingStrategy} implementation to use
 * for fetching Telegram updates in polling mode.
 *
 * <p>Configured via {@code jaiclaw.channels.telegram.polling-strategy}.
 *
 * <ul>
 *   <li>{@link #CAMEL} — (default) Delegates to Apache Camel's {@code camel-telegram}
 *       consumer. Handles long-polling, retry, backoff, and thread management internally.
 *       Requires {@code camel-telegram-starter} on the classpath.</li>
 *   <li>{@link #NATIVE} — Hand-rolled polling loop on a virtual thread.
 *       Zero additional dependencies. Use when Camel is not available.</li>
 * </ul>
 */
public enum TelegramPollingStrategyType {

    CAMEL,
    NATIVE;

    /**
     * Parse a config string (case-insensitive).
     * Returns {@link #NATIVE} for null/blank/unrecognized values.
     */
    public static TelegramPollingStrategyType fromString(String value) {
        if (value == null || value.isBlank()) return NATIVE;
        return switch (value.strip().toLowerCase()) {
            case "camel" -> CAMEL;
            case "native" -> NATIVE;
            default -> NATIVE;
        };
    }
}
