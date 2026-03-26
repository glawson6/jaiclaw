package io.jaiclaw.core.agent;

import java.util.Map;

/**
 * Event record for tool call lifecycle hooks (BEFORE_TOOL_CALL / AFTER_TOOL_CALL).
 *
 * @param toolName        the name of the tool being called
 * @param parameters      the tool call parameters (as JSON string)
 * @param result          the tool result (null for BEFORE events)
 * @param iterationNumber the current iteration in the tool loop (1-based)
 * @param sessionKey      the session key for context
 */
public record ToolCallEvent(
        String toolName,
        String parameters,
        String result,
        int iterationNumber,
        String sessionKey
) {
    public static ToolCallEvent before(String toolName, String parameters,
                                       int iterationNumber, String sessionKey) {
        return new ToolCallEvent(toolName, parameters, null, iterationNumber, sessionKey);
    }

    public static ToolCallEvent after(String toolName, String parameters, String result,
                                      int iterationNumber, String sessionKey) {
        return new ToolCallEvent(toolName, parameters, result, iterationNumber, sessionKey);
    }
}
