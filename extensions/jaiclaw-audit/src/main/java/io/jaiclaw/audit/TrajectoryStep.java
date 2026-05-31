package io.jaiclaw.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * A single step in an agent trajectory — records what happened at each
 * decision point during an agent execution loop.
 *
 * @param stepIndex      ordinal position in the trajectory (0-based)
 * @param stepType       the type of step (LLM_CALL, TOOL_CALL, COMPACTION, etc.)
 * @param toolName       name of the tool invoked (null for non-tool steps)
 * @param inputSummary   truncated summary of the input to this step
 * @param outputSummary  truncated summary of the output from this step
 * @param tokenCount     number of tokens consumed by this step (0 if unknown)
 * @param duration       wall-clock duration of this step
 * @param timestamp      when this step started
 * @param traceId        distributed trace ID for correlation (nullable)
 * @param metadata       additional provider-specific data
 */
public record TrajectoryStep(
        int stepIndex,
        StepType stepType,
        String toolName,
        String inputSummary,
        String outputSummary,
        int tokenCount,
        Duration duration,
        Instant timestamp,
        String traceId,
        Map<String, Object> metadata
) {

    public enum StepType {
        LLM_CALL,
        TOOL_CALL,
        COMPACTION,
        MEMORY_SEARCH,
        HOOK,
        SYSTEM
    }

    public TrajectoryStep {
        if (stepType == null) stepType = StepType.SYSTEM;
        if (timestamp == null) timestamp = Instant.now();
        if (duration == null) duration = Duration.ZERO;
        if (metadata == null) metadata = Map.of();
        if (inputSummary == null) inputSummary = "";
        if (outputSummary == null) outputSummary = "";
    }

    /**
     * Converts this step into an {@link AuditEvent} for persistence via {@link AuditLogger}.
     */
    public AuditEvent toAuditEvent(String tenantId, String sessionKey) {
        Map<String, Object> details = new java.util.HashMap<>(metadata);
        details.put("stepIndex", stepIndex);
        details.put("stepType", stepType.name());
        details.put("inputSummary", inputSummary);
        details.put("outputSummary", outputSummary);
        details.put("tokenCount", tokenCount);
        details.put("durationMs", duration.toMillis());
        if (traceId != null) {
            details.put("traceId", traceId);
        }

        String action = "trajectory." + stepType.name().toLowerCase();
        String resource = toolName != null ? toolName : sessionKey;

        return AuditEvent.builder()
                .id("traj-" + sessionKey + "-" + stepIndex)
                .tenantId(tenantId)
                .actor("agent")
                .action(action)
                .resource(resource)
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details(details)
                .build();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int stepIndex;
        private StepType stepType;
        private String toolName;
        private String inputSummary;
        private String outputSummary;
        private int tokenCount;
        private Duration duration;
        private Instant timestamp;
        private String traceId;
        private Map<String, Object> metadata;

        public Builder stepIndex(int stepIndex) { this.stepIndex = stepIndex; return this; }
        public Builder stepType(StepType stepType) { this.stepType = stepType; return this; }
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder inputSummary(String inputSummary) { this.inputSummary = inputSummary; return this; }
        public Builder outputSummary(String outputSummary) { this.outputSummary = outputSummary; return this; }
        public Builder tokenCount(int tokenCount) { this.tokenCount = tokenCount; return this; }
        public Builder duration(Duration duration) { this.duration = duration; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public TrajectoryStep build() {
            return new TrajectoryStep(stepIndex, stepType, toolName, inputSummary, outputSummary,
                    tokenCount, duration, timestamp, traceId, metadata);
        }
    }
}
