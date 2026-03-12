package io.jclaw.tools.bridge.embabel;

import java.util.Map;

/**
 * Result of an orchestrated workflow execution.
 *
 * @param output    the output text/content from the workflow
 * @param metadata  additional structured data from the execution
 * @param success   whether the execution completed successfully
 * @param error     error message if execution failed (null on success)
 */
public record OrchestrationResult(
        String output,
        Map<String, Object> metadata,
        boolean success,
        String error
) {
    public OrchestrationResult {
        if (output == null) output = "";
        if (metadata == null) metadata = Map.of();
    }

    public static OrchestrationResult success(String output) {
        return new OrchestrationResult(output, Map.of(), true, null);
    }

    public static OrchestrationResult success(String output, Map<String, Object> metadata) {
        return new OrchestrationResult(output, metadata, true, null);
    }

    public static OrchestrationResult failure(String error) {
        return new OrchestrationResult("", Map.of(), false, error);
    }
}
