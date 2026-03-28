package io.jaiclaw.core.tool;

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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String agentId;
        private String sessionKey;
        private String sessionId;
        private String workspaceDir;
        private Map<String, Object> contextData;

        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder sessionKey(String sessionKey) { this.sessionKey = sessionKey; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder workspaceDir(String workspaceDir) { this.workspaceDir = workspaceDir; return this; }
        public Builder contextData(Map<String, Object> contextData) { this.contextData = contextData; return this; }

        public ToolContext build() {
            return new ToolContext(agentId, sessionKey, sessionId, workspaceDir, contextData);
        }
    }
}
