package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired when a channel adapter receives an inbound message.
 *
 * <p>Currently registered by several plugins ({@code TelegramDocStorePlugin},
 * {@code TelegramSubscriptionPlugin}, {@code ObservabilityPlugin}) but not
 * yet wired from any channel adapter — the dispatch will land in a future
 * PR. Defined here so plugins can opt in to the typed API today.
 */
public record MessageReceivedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String channelId,
        String accountId,
        String peerId,
        String content
) implements HookEvent {

    public static MessageReceivedEvent of(String agentId, String sessionKey, String channelId,
                                           String accountId, String peerId, String content) {
        return new MessageReceivedEvent(agentId, sessionKey, Instant.now(), channelId, accountId, peerId, content);
    }
}
