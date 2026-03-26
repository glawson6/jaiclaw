package io.jaiclaw.core.agent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * SPI for human-in-the-loop tool approval. When enabled, the agent runtime
 * calls this before executing each tool to get explicit approval.
 */
public interface ToolApprovalHandler {

    /**
     * Request approval for a tool call.
     *
     * @param toolName   the name of the tool being called
     * @param parameters the tool call parameters
     * @param sessionKey the session key for routing the approval request
     * @return a future that completes with the approval decision
     */
    CompletableFuture<ToolApprovalDecision> requestApproval(
            String toolName, Map<String, Object> parameters, String sessionKey);
}
