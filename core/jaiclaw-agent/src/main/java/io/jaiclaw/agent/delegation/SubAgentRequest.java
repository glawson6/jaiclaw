package io.jaiclaw.agent.delegation;

import io.jaiclaw.agent.AgentRuntimeContext;
import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.tool.ToolProfile;

/**
 * A request to run a bounded child agent on behalf of a parent run.
 *
 * @param goal          what the child should accomplish; becomes its user message
 * @param context       extra background the parent wants the child to have; may be null
 * @param parentContext the parent's runtime context — supplies tenant, agent id,
 *                      workspace, delegation depth and the parent's tool profile
 * @param toolProfile   requested profile for the child; narrowed to the parent's if
 *                      wider, null means "inherit the parent's"
 * @param maxIterations child iteration budget; {@code <= 0} means "use the configured default"
 * @param waitForResult when true the caller blocks for the result (up to the
 *                      configured wait timeout); when false a handle is returned
 *                      immediately and polled via {@code delegate_status}.
 *                      Named {@code waitForResult} rather than {@code wait}
 *                      because {@code wait} is an Object method and cannot be a
 *                      record component.
 *
 * <p>Phase 2 of the 1.2.0 plan.
 */
@Experimental
public record SubAgentRequest(
        String goal,
        String context,
        AgentRuntimeContext parentContext,
        ToolProfile toolProfile,
        int maxIterations,
        boolean waitForResult
) {
    public SubAgentRequest {
        if (goal != null) goal = goal.strip();
    }

    /** True when the goal is missing or blank — the one unrecoverable input error. */
    public boolean hasBlankGoal() {
        return goal == null || goal.isBlank();
    }

    /**
     * The child's user message: the goal, plus the parent's supplied context when
     * present. Kept here rather than in the launcher so the prompt shape is part
     * of the request contract and can be asserted in tests.
     */
    public String toPrompt() {
        if (context == null || context.isBlank()) return goal;
        return goal + "\n\nContext from the delegating agent:\n" + context;
    }
}
