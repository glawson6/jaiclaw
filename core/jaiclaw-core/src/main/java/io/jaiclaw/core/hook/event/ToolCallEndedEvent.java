package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired after a tool returns (or fails).
 *
 * <p>Replaces the pre-0.8.0 {@code AFTER_TOOL_CALL} hook payload.
 *
 * <p>{@code success} is conventionally {@code false} when the {@code result}
 * begins with {@code "ERROR:"} (the framework's standard error prefix) or
 * the tool was denied by the approval handler.
 */
public record ToolCallEndedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String toolName,
        String parameters,
        String result,
        boolean success,
        int iterationNumber
) implements HookEvent {

    public static ToolCallEndedEvent of(String agentId, String sessionKey, String toolName,
                                         String parameters, String result, int iterationNumber) {
        boolean success = result != null && !result.startsWith("ERROR:") && !result.startsWith("Tool call denied:");
        return new ToolCallEndedEvent(agentId, sessionKey, Instant.now(), toolName, parameters, result, success, iterationNumber);
    }
}
