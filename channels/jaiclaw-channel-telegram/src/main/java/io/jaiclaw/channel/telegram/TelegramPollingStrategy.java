package io.jaiclaw.channel.telegram;

/**
 * Strategy for polling Telegram Bot API for updates.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@link CamelTelegramPollingStrategy} — delegates to Apache Camel's
 *       {@code camel-telegram} consumer (default when Camel is on the classpath)</li>
 *   <li>{@link NativeTelegramPollingStrategy} — hand-rolled polling loop on a
 *       virtual thread (fallback when Camel is not available)</li>
 * </ul>
 *
 * <p>Mirrors the {@link TelegramHttpClient} / {@link TelegramHttpClientType}
 * strategy pattern already in this module.
 *
 * @see TelegramPollingStrategyType
 */
public interface TelegramPollingStrategy {

    /**
     * Start polling for Telegram updates. Called once from
     * {@link TelegramAdapter#start}.
     *
     * @param config        Telegram adapter configuration
     * @param updateHandler callback to deliver raw update JSON nodes
     */
    void startPolling(TelegramConfig config, TelegramUpdateHandler updateHandler);

    /**
     * Stop polling. Must be idempotent.
     */
    void stopPolling();

    /**
     * Check if polling is currently active.
     */
    boolean isPolling();
}
