package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired when a new session is created in {@code SessionManager}.
 *
 * <p>Aspirational in 0.8.0 — not yet wired.
 */
public record SessionStartedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp
) implements HookEvent {

    public static SessionStartedEvent of(String agentId, String sessionKey) {
        return new SessionStartedEvent(agentId, sessionKey, Instant.now());
    }
}
