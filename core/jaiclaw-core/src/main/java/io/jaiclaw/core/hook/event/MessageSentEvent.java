package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired after a channel adapter delivers an outbound message.
 *
 * <p>Aspirational in 0.8.0 — not yet wired.
 */
public record MessageSentEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String channelId,
        String accountId,
        String peerId,
        String content,
        boolean delivered
) implements HookEvent {

    public static MessageSentEvent of(String agentId, String sessionKey, String channelId,
                                       String accountId, String peerId, String content, boolean delivered) {
        return new MessageSentEvent(agentId, sessionKey, Instant.now(), channelId, accountId, peerId, content, delivered);
    }
}
