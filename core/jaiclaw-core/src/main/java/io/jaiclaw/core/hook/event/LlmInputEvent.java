package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired when the agent runtime sends user input to the LLM.
 *
 * <p>Replaces the pre-0.8.0 {@code LLM_INPUT} hook payload (which was the
 * raw {@code String userInput}).
 */
public record LlmInputEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String userInput
) implements HookEvent {

    public static LlmInputEvent of(String agentId, String sessionKey, String userInput) {
        return new LlmInputEvent(agentId, sessionKey, Instant.now(), userInput);
    }
}
