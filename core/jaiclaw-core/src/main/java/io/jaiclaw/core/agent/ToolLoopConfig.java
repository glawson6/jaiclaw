package io.jaiclaw.core.agent;

import java.util.Map;

/**
 * Configuration for the tool execution loop.
 *
 * <p>The three original components ({@code mode}, {@code maxIterations},
 * {@code requireApproval}) are unchanged. 1.2.0 adds the runtime guards; every
 * one of them defaults to today's behaviour, so a config built with the legacy
 * three-argument constructor behaves exactly as it did before.
 *
 * @param mode                 SPRING_AI uses ChatClient's built-in loop; EXPLICIT uses JaiClaw's
 *                             own loop with hook observability and approval gates
 * @param maxIterations        maximum number of tool call iterations before stopping
 * @param requireApproval      whether to require human approval before each tool execution
 * @param budgetTemplate       template budget for a run; {@link IterationBudget#forRun()} is
 *                             called per run so parent and child runs never share a counter.
 *                             Defaults to a budget of {@code maxIterations}.
 * @param budgetWarningRatio   fraction of the budget at which the loop injects a one-time
 *                             "wrap up" notice into the next tool result (default {@code 0.9});
 *                             values outside {@code (0.0, 1.0]} disable the warning
 * @param repetitionThreshold  number of identical consecutive tool calls that trips the
 *                             repetition guard (default {@code 3}); {@code <= 0} disables it
 * @param approvalFloors       per-tool minimum approval posture, keyed by tool name; tools
 *                             absent from the map use {@link ApprovalFloor#NONE}
 */
public record ToolLoopConfig(
        Mode mode,
        int maxIterations,
        boolean requireApproval,
        IterationBudget budgetTemplate,
        double budgetWarningRatio,
        int repetitionThreshold,
        Map<String, ApprovalFloor> approvalFloors
) {
    public enum Mode { SPRING_AI, EXPLICIT }

    /** Default warning ratio — warn once 90% of the budget is spent. */
    public static final double DEFAULT_WARNING_RATIO = 0.9;

    /** Default repetition threshold — three identical consecutive calls. */
    public static final int DEFAULT_REPETITION_THRESHOLD = 3;

    public static final ToolLoopConfig DEFAULT = new ToolLoopConfig(Mode.SPRING_AI, 25, false);

    public ToolLoopConfig {
        if (maxIterations <= 0) maxIterations = 25;
        if (budgetTemplate == null) budgetTemplate = IterationBudget.of(maxIterations);
        if (budgetWarningRatio <= 0.0 || budgetWarningRatio > 1.0) budgetWarningRatio = DEFAULT_WARNING_RATIO;
        if (repetitionThreshold < 0) repetitionThreshold = DEFAULT_REPETITION_THRESHOLD;
        approvalFloors = approvalFloors == null ? Map.of() : Map.copyOf(approvalFloors);
    }

    /**
     * Legacy three-argument constructor, preserved so existing call sites compile
     * unchanged. Guards take their defaults: budget = {@code maxIterations},
     * warning ratio {@value #DEFAULT_WARNING_RATIO}, repetition threshold
     * {@value #DEFAULT_REPETITION_THRESHOLD}, no approval floors.
     */
    public ToolLoopConfig(Mode mode, int maxIterations, boolean requireApproval) {
        this(mode, maxIterations, requireApproval,
                null, DEFAULT_WARNING_RATIO, DEFAULT_REPETITION_THRESHOLD, Map.of());
    }

    /** The approval floor configured for {@code toolName}; never null. */
    public ApprovalFloor floorFor(String toolName) {
        if (toolName == null) return ApprovalFloor.NONE;
        return approvalFloors.getOrDefault(toolName, ApprovalFloor.NONE);
    }

    /** A fresh per-run budget derived from {@link #budgetTemplate()}. */
    public IterationBudget newRunBudget() {
        return budgetTemplate.forRun();
    }

    /** True when the repetition guard is active. */
    public boolean repetitionGuardEnabled() {
        return repetitionThreshold > 0;
    }
}
