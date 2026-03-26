package io.jaiclaw.core.agent;

import java.util.Map;

/**
 * Sealed interface representing the result of a tool approval request.
 */
public sealed interface ToolApprovalDecision
        permits ToolApprovalDecision.Approved,
                ToolApprovalDecision.Denied,
                ToolApprovalDecision.Modified {

    record Approved() implements ToolApprovalDecision {}

    record Denied(String reason) implements ToolApprovalDecision {}

    record Modified(Map<String, Object> parameters) implements ToolApprovalDecision {}
}
