package io.jclaw.core.tool;

import java.util.Map;

/**
 * Runtime context provided to a tool during execution.
 */
public record ToolContext(
        String agentId,
        String sessionKey,
        String sessionId,
        String workspaceDir,
        Map<String, Object> contextData
) {
    public ToolContext(String agentId, String sessionKey, String sessionId, String workspaceDir) {
        this(agentId, sessionKey, sessionId, workspaceDir, Map.of());
    }
}
