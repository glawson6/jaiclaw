package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired before context compaction begins.
 *
 * <p>Aspirational in 0.8.0 — registered by example plugins
 * ({@code ResearchAssistantPlugin}) but not yet fired by the framework.
 */
public record BeforeCompactionEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        int currentTokens,
        int targetTokens
) implements HookEvent {

    public static BeforeCompactionEvent of(String agentId, String sessionKey,
                                            int currentTokens, int targetTokens) {
        return new BeforeCompactionEvent(agentId, sessionKey, Instant.now(), currentTokens, targetTokens);
    }
}
