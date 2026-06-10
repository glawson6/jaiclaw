package io.jaiclaw.pipeline.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of {@link PipelineValidator#validate()}. Groups errors by pipeline so
 * the consolidated message can list every issue per pipeline together.
 *
 * @param byPipeline errors keyed by pipeline ID (preserves insertion order)
 * @param global     errors that are not specific to a single pipeline
 */
public record ValidationReport(
        Map<String, List<ValidationError>> byPipeline,
        List<ValidationError> global
) {
    public ValidationReport {
        byPipeline = byPipeline == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(byPipeline));
        global = global == null ? List.of() : List.copyOf(global);
    }

    public boolean hasErrors() {
        if (!global.isEmpty()) return true;
        for (List<ValidationError> errors : byPipeline.values()) {
            if (errors != null && !errors.isEmpty()) return true;
        }
        return false;
    }

    /** Total number of errors across all pipelines. */
    public int totalErrors() {
        int total = global.size();
        for (List<ValidationError> errors : byPipeline.values()) {
            if (errors != null) total += errors.size();
        }
        return total;
    }

    /**
     * Render the report as a single human-readable string. Each pipeline gets a
     * header followed by indented bullet lines (one per error).
     */
    public String formatted() {
        if (!hasErrors()) {
            return "Pipeline validation: no errors";
        }
        StringBuilder sb = new StringBuilder();
        if (!global.isEmpty()) {
            sb.append("Pipeline validation failed (global):\n");
            for (ValidationError e : global) {
                sb.append(e.formatted()).append('\n');
            }
        }
        for (Map.Entry<String, List<ValidationError>> entry : byPipeline.entrySet()) {
            List<ValidationError> errors = entry.getValue();
            if (errors == null || errors.isEmpty()) continue;
            sb.append("Pipeline '").append(entry.getKey()).append("' failed validation:\n");
            for (ValidationError e : errors) {
                sb.append(e.formatted()).append('\n');
            }
        }
        // Trim trailing newline
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == '\n') sb.setLength(len - 1);
        return sb.toString();
    }

    /** Builder used by {@link PipelineValidator}. */
    public static final class Builder {
        private final Map<String, List<ValidationError>> byPipeline = new LinkedHashMap<>();
        private final List<ValidationError> global = new ArrayList<>();

        public Builder addPipelineError(String pipelineId, ValidationError error) {
            byPipeline.computeIfAbsent(pipelineId, k -> new ArrayList<>()).add(error);
            return this;
        }

        public Builder addGlobalError(ValidationError error) {
            global.add(error);
            return this;
        }

        public ValidationReport build() {
            return new ValidationReport(byPipeline, global);
        }
    }
}
