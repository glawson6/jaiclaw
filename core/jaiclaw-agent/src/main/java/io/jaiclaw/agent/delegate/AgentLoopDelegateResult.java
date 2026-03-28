package io.jaiclaw.agent.delegate;

import java.util.Map;

/**
 * Result from an {@link AgentLoopDelegate} execution.
 *
 * @param content  assistant response text
 * @param success  true if execution completed successfully
 * @param metadata delegate-specific metadata (e.g., tools called, workflow steps)
 */
public record AgentLoopDelegateResult(
        String content,
        boolean success,
        Map<String, Object> metadata
) {
    public AgentLoopDelegateResult {
        if (metadata == null) metadata = Map.of();
    }

    public static AgentLoopDelegateResult success(String content) {
        return new AgentLoopDelegateResult(content, true, Map.of());
    }

    public static AgentLoopDelegateResult failure(String content) {
        return new AgentLoopDelegateResult(content, false, Map.of());
    }
}
