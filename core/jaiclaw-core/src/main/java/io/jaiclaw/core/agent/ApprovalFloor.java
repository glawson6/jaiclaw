package io.jaiclaw.core.agent;

import io.jaiclaw.core.api.Experimental;

/**
 * The minimum approval posture a tool may be granted, regardless of what the
 * session or the {@link ToolApprovalHandler} would otherwise allow.
 *
 * <p>Floors exist so an operator can say "no matter what the model asks for, and
 * no matter what a user clicked earlier, {@code shell_exec} is always confirmed
 * individually". They are configured per tool name via
 * {@link ToolLoopConfig#approvalFloors()} and applied by the tool loop
 * <em>before</em> the approval handler is consulted.
 *
 * <p>A floor can only make approval stricter, never looser. Tools with no
 * configured floor default to {@link #NONE}, which preserves today's behaviour
 * exactly.
 *
 * @see ToolLoopConfig#approvalFloors()
 */
@Experimental
public enum ApprovalFloor {

    /**
     * No floor. The tool follows the session's normal approval configuration —
     * this is the default for every tool that is not explicitly listed.
     */
    NONE,

    /**
     * The tool must be approved on <em>every</em> call. A blanket "allow always"
     * grant is downgraded to a single-use approval, so the handler is asked again
     * on the next call. Use for tools whose blast radius justifies a per-call
     * confirmation ({@code shell_exec}, {@code file_write}).
     */
    PROMPT_ALWAYS,

    /**
     * The tool is refused without consulting the approval handler at all. The
     * loop returns a denial as the tool result, so the model can react and
     * choose another path rather than the run failing.
     */
    DENY
}
