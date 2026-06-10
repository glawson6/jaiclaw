package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired when a session is closed or expires.
 *
 * <p>Aspirational in 0.8.0 — not yet wired.
 */
public record SessionEndedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String reason
) implements HookEvent {

    public static SessionEndedEvent of(String agentId, String sessionKey, String reason) {
        return new SessionEndedEvent(agentId, sessionKey, Instant.now(), reason);
    }
}
