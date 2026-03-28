package io.jaiclaw.tools.bridge.embabel;

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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String output;
        private Map<String, Object> metadata;
        private boolean success;
        private String error;

        public Builder output(String output) { this.output = output; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder success(boolean success) { this.success = success; return this; }
        public Builder error(String error) { this.error = error; return this; }

        public OrchestrationResult build() {
            return new OrchestrationResult(output, metadata, success, error);
        }
    }
}
