package io.jaiclaw.config;

import io.jaiclaw.core.agent.ApprovalFloor;
import io.jaiclaw.core.agent.IterationBudget;
import io.jaiclaw.core.agent.ToolLoopConfig;

import java.util.Map;

/**
 * Configuration properties for the tool execution loop, bound from
 * {@code jaiclaw.agent.agents.<name>.tool-loop} in application.yml.
 *
 * <p><strong>Binder note.</strong> This record is bound by Spring Boot's
 * constructor binder, so per the repo rule it exposes exactly one public
 * constructor — the canonical one. Programmatic defaults live on
 * {@link #DEFAULT} and {@link #defaults()}, never on a convenience overload,
 * because Boot 4's {@code Instantiator} picks a constructor by parameter count
 * and would silently drop nested YAML values if an overload existed.
 *
 * @param mode                "spring-ai" or "explicit"
 * @param maxIterations       maximum tool call iterations
 * @param requireApproval     whether to require human approval before each tool call
 * @param budgetMaxIterations per-run iteration budget; {@code <= 0} means "use
 *                            {@code maxIterations}". Split from {@code maxIterations}
 *                            so a delegated child run can carry a smaller budget than
 *                            the loop's hard cap.
 * @param budgetWarningRatio  fraction of the budget at which the loop tells the model
 *                            to wrap up (default {@code 0.9}); outside {@code (0,1]} disables it
 * @param repetitionThreshold identical consecutive tool calls that trip the repetition
 *                            guard (default {@code 3}); {@code 0} disables it
 * @param approvalFloors      per-tool minimum approval posture, e.g.
 *                            {@code {shell_exec: PROMPT_ALWAYS}}
 */
public record ToolLoopProperties(
        String mode,
        int maxIterations,
        boolean requireApproval,
        int budgetMaxIterations,
        double budgetWarningRatio,
        int repetitionThreshold,
        Map<String, ApprovalFloor> approvalFloors
) {
    public static final ToolLoopProperties DEFAULT = defaults();

    public ToolLoopProperties {
        if (mode == null) mode = "spring-ai";
        if (maxIterations <= 0) maxIterations = 25;
        if (budgetWarningRatio <= 0.0 || budgetWarningRatio > 1.0) {
            budgetWarningRatio = ToolLoopConfig.DEFAULT_WARNING_RATIO;
        }
        if (repetitionThreshold < 0) repetitionThreshold = ToolLoopConfig.DEFAULT_REPETITION_THRESHOLD;
        approvalFloors = approvalFloors == null ? Map.of() : Map.copyOf(approvalFloors);
    }

    /**
     * Programmatic defaults for tests and builders. The binder never sees this
     * factory, so it cannot be mistaken for a bindable constructor.
     */
    public static ToolLoopProperties defaults() {
        return new ToolLoopProperties("spring-ai", 25, false,
                0, ToolLoopConfig.DEFAULT_WARNING_RATIO,
                ToolLoopConfig.DEFAULT_REPETITION_THRESHOLD, Map.of());
    }

    public ToolLoopConfig toConfig() {
        var configMode = "explicit".equalsIgnoreCase(mode)
                ? ToolLoopConfig.Mode.EXPLICIT
                : ToolLoopConfig.Mode.SPRING_AI;
        int budgetSize = budgetMaxIterations > 0 ? budgetMaxIterations : maxIterations;
        return new ToolLoopConfig(configMode, maxIterations, requireApproval,
                IterationBudget.of(budgetSize), budgetWarningRatio,
                repetitionThreshold, approvalFloors);
    }
}
