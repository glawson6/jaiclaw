package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired before {@code AgentRuntime} begins processing user input.
 *
 * <p>Replaces the pre-0.8.0 {@code BEFORE_AGENT_START} hook payload (which was
 * just the raw {@code String userInput}).
 */
public record AgentStartedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String userInput
) implements HookEvent {

    public static AgentStartedEvent of(String agentId, String sessionKey, String userInput) {
        return new AgentStartedEvent(agentId, sessionKey, Instant.now(), userInput);
    }
}
