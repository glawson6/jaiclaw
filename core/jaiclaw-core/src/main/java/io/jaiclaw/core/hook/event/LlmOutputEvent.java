package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired after the agent runtime receives a response from the LLM.
 *
 * <p>Replaces the pre-0.8.0 {@code LLM_OUTPUT} hook payload (which was the
 * raw {@code String responseContent}).
 */
public record LlmOutputEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String responseContent
) implements HookEvent {

    public static LlmOutputEvent of(String agentId, String sessionKey, String responseContent) {
        return new LlmOutputEvent(agentId, sessionKey, Instant.now(), responseContent);
    }
}
