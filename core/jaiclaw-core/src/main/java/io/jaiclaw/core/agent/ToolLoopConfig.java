package io.jaiclaw.core.agent;

/**
 * Configuration for the tool execution loop.
 *
 * @param mode            SPRING_AI uses ChatClient's built-in loop; EXPLICIT uses JaiClaw's
 *                        own loop with hook observability and approval gates
 * @param maxIterations   maximum number of tool call iterations before stopping
 * @param requireApproval whether to require human approval before each tool execution
 */
public record ToolLoopConfig(
        Mode mode,
        int maxIterations,
        boolean requireApproval
) {
    public enum Mode { SPRING_AI, EXPLICIT }

    public static final ToolLoopConfig DEFAULT = new ToolLoopConfig(Mode.SPRING_AI, 25, false);

    public ToolLoopConfig {
        if (maxIterations <= 0) maxIterations = 25;
    }
}
