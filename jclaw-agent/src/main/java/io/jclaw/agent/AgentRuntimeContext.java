package io.jclaw.agent;

import io.jclaw.core.model.AgentIdentity;
import io.jclaw.core.model.Session;
import io.jclaw.core.tool.ToolProfile;

/**
 * Context provided to the agent runtime for a single execution.
 */
public record AgentRuntimeContext(
        String agentId,
        String sessionKey,
        Session session,
        AgentIdentity identity,
        ToolProfile toolProfile,
        String workspaceDir
) {
    public AgentRuntimeContext(String agentId, String sessionKey, Session session) {
        this(agentId, sessionKey, session, AgentIdentity.DEFAULT, ToolProfile.FULL, ".");
    }
}
