package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired before a channel adapter sends an outbound message.
 *
 * <p>Aspirational in 0.8.0 — not yet wired.
 */
public record MessageSendingEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String channelId,
        String accountId,
        String peerId,
        String content
) implements HookEvent {

    public static MessageSendingEvent of(String agentId, String sessionKey, String channelId,
                                          String accountId, String peerId, String content) {
        return new MessageSendingEvent(agentId, sessionKey, Instant.now(), channelId, accountId, peerId, content);
    }
}
