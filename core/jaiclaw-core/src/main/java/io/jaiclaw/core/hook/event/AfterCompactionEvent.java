package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired after context compaction completes.
 *
 * <p>Aspirational in 0.8.0 — not yet wired.
 */
public record AfterCompactionEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        int tokensSavedBefore,
        int tokensSavedAfter
) implements HookEvent {

    public static AfterCompactionEvent of(String agentId, String sessionKey,
                                           int tokensSavedBefore, int tokensSavedAfter) {
        return new AfterCompactionEvent(agentId, sessionKey, Instant.now(), tokensSavedBefore, tokensSavedAfter);
    }
}
