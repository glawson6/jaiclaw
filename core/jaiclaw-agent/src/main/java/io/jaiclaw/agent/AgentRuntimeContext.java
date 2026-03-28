package io.jaiclaw.agent;

import io.jaiclaw.config.TenantAgentConfig;
import io.jaiclaw.core.model.AgentIdentity;
import io.jaiclaw.core.model.Session;
import io.jaiclaw.core.tool.ToolProfile;

/**
 * Context provided to the agent runtime for a single execution.
 *
 * @param agentId      agent identifier
 * @param sessionKey   session key
 * @param session      conversation session
 * @param identity     agent identity (name, description)
 * @param toolProfile  tool profile for tool filtering
 * @param workspaceDir workspace directory for memory loading
 * @param tenantConfig per-tenant agent configuration (nullable — null means use singleton path)
 */
public record AgentRuntimeContext(
        String agentId,
        String sessionKey,
        Session session,
        AgentIdentity identity,
        ToolProfile toolProfile,
        String workspaceDir,
        TenantAgentConfig tenantConfig
) {
    /**
     * Backward-compatible constructor without tenantConfig.
     */
    public AgentRuntimeContext(String agentId, String sessionKey, Session session,
                               AgentIdentity identity, ToolProfile toolProfile, String workspaceDir) {
        this(agentId, sessionKey, session, identity, toolProfile, workspaceDir, null);
    }

    /**
     * Minimal backward-compatible constructor.
     */
    public AgentRuntimeContext(String agentId, String sessionKey, Session session) {
        this(agentId, sessionKey, session, AgentIdentity.DEFAULT, ToolProfile.FULL, ".", null);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String agentId;
        private String sessionKey;
        private Session session;
        private AgentIdentity identity;
        private ToolProfile toolProfile;
        private String workspaceDir;
        private TenantAgentConfig tenantConfig;

        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder sessionKey(String sessionKey) { this.sessionKey = sessionKey; return this; }
        public Builder session(Session session) { this.session = session; return this; }
        public Builder identity(AgentIdentity identity) { this.identity = identity; return this; }
        public Builder toolProfile(ToolProfile toolProfile) { this.toolProfile = toolProfile; return this; }
        public Builder workspaceDir(String workspaceDir) { this.workspaceDir = workspaceDir; return this; }
        public Builder tenantConfig(TenantAgentConfig tenantConfig) { this.tenantConfig = tenantConfig; return this; }

        public AgentRuntimeContext build() {
            return new AgentRuntimeContext(
                    agentId, sessionKey, session, identity, toolProfile, workspaceDir, tenantConfig);
        }
    }
}
