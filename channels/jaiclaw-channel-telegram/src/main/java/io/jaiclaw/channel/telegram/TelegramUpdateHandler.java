package io.jaiclaw.channel.telegram;

import tools.jackson.databind.JsonNode;

/**
 * Callback for delivering raw Telegram update JSON to the adapter.
 *
 * <p>Decouples {@link TelegramPollingStrategy} implementations from
 * {@link TelegramAdapter} internals — the strategy only needs to
 * deliver raw update nodes; the adapter handles parsing and dispatch.
 */
@FunctionalInterface
public interface TelegramUpdateHandler {

    /**
     * Process a single Telegram update.
     *
     * @param update raw Telegram update JSON node
     */
    void onUpdate(JsonNode update);
}
