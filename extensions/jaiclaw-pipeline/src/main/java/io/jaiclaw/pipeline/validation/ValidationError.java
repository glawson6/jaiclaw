package io.jaiclaw.pipeline.validation;

/**
 * A single validation problem detected for a pipeline.
 *
 * @param pipelineId pipeline that owns the error (may be {@code "*"} for global errors)
 * @param location   human-readable location within the pipeline (e.g. {@code "stage 'write'"})
 * @param code       short machine code (e.g. {@code "UNKNOWN_STAGE_REF"})
 * @param message    full human-readable message
 * @param suggestion optional "did you mean X?" hint (nullable)
 */
public record ValidationError(
        String pipelineId,
        String location,
        String code,
        String message,
        String suggestion
) {
    public ValidationError {
        if (pipelineId == null || pipelineId.isBlank()) {
            pipelineId = "*";
        }
        if (code == null || code.isBlank()) {
            code = "INVALID";
        }
        if (message == null) {
            message = "";
        }
    }

    /** Render as a single bullet line: {@code "  - <location>: <message> — did you mean '<x>'?"}. */
    public String formatted() {
        StringBuilder sb = new StringBuilder("  - ");
        if (location != null && !location.isBlank()) {
            sb.append(location).append(": ");
        }
        sb.append(message);
        if (suggestion != null && !suggestion.isBlank()) {
            sb.append(" — did you mean '").append(suggestion).append("'?");
        }
        return sb.toString();
    }
}
