package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired before a tool is invoked.
 *
 * <p>Replaces the pre-0.8.0 {@code BEFORE_TOOL_CALL} hook payload (which was
 * the {@code ToolCallEvent.before(...)} factory result on the now-deprecated
 * {@link io.jaiclaw.core.agent.ToolCallEvent} type).
 */
public record ToolCallStartedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String toolName,
        String parameters,
        int iterationNumber
) implements HookEvent {

    public static ToolCallStartedEvent of(String agentId, String sessionKey, String toolName,
                                           String parameters, int iterationNumber) {
        return new ToolCallStartedEvent(agentId, sessionKey, Instant.now(), toolName, parameters, iterationNumber);
    }
}
