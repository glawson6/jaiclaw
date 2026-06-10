package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired before a session is reset.
 *
 * <p>Aspirational in 0.8.0 — not yet wired.
 */
public record BeforeResetEvent(
        String agentId,
        String sessionKey,
        Instant timestamp
) implements HookEvent {

    public static BeforeResetEvent of(String agentId, String sessionKey) {
        return new BeforeResetEvent(agentId, sessionKey, Instant.now());
    }
}
