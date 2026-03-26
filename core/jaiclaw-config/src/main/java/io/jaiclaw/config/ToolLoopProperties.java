package io.jaiclaw.config;

import io.jaiclaw.core.agent.ToolLoopConfig;

/**
 * Configuration properties for the tool execution loop, bound from
 * {@code jaiclaw.agent.agents.<name>.tool-loop} in application.yml.
 *
 * @param mode            "spring-ai" or "explicit"
 * @param maxIterations   maximum tool call iterations
 * @param requireApproval whether to require human approval before each tool call
 */
public record ToolLoopProperties(
        String mode,
        int maxIterations,
        boolean requireApproval
) {
    public static final ToolLoopProperties DEFAULT = new ToolLoopProperties("spring-ai", 25, false);

    public ToolLoopProperties {
        if (mode == null) mode = "spring-ai";
        if (maxIterations <= 0) maxIterations = 25;
    }

    public ToolLoopConfig toConfig() {
        var configMode = "explicit".equalsIgnoreCase(mode)
                ? ToolLoopConfig.Mode.EXPLICIT
                : ToolLoopConfig.Mode.SPRING_AI;
        return new ToolLoopConfig(configMode, maxIterations, requireApproval);
    }
}
