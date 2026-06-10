package io.jaiclaw.pipeline.tracking;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable snapshot of a single pipeline execution. Stored in
 * {@link PipelineExecutionTracker} for operational visibility and exposed via
 * the {@code /actuator/pipelines} endpoint.
 *
 * @param executionId    unique execution UUID
 * @param pipelineId     pipeline definition ID
 * @param tenantId       tenant ID (nullable for single-tenant)
 * @param startedAt      when the execution began
 * @param completedAt    when it finished (nullable while RUNNING)
 * @param status         lifecycle state
 * @param currentStage   name of the stage currently executing (nullable when not RUNNING)
 * @param stageDurations per-stage execution durations
 * @param failureReason  failure message, truncated (nullable unless FAILED)
 * @param totalDuration  total wall-clock duration (nullable while RUNNING)
 */
public record PipelineExecutionSummary(
        String executionId,
        String pipelineId,
        String tenantId,
        Instant startedAt,
        Instant completedAt,
        ExecutionStatus status,
        String currentStage,
        Map<String, Duration> stageDurations,
        String failureReason,
        Duration totalDuration
) {
    /** Maximum stored bytes for a failure reason — keeps the tracker memory-bounded. */
    public static final int MAX_FAILURE_REASON_BYTES = 4096;

    public PipelineExecutionSummary {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("pipelineId must not be blank");
        }
        if (status == null) status = ExecutionStatus.RUNNING;
        if (stageDurations == null) {
            stageDurations = Map.of();
        } else {
            stageDurations = Collections.unmodifiableMap(new LinkedHashMap<>(stageDurations));
        }
        if (failureReason != null && failureReason.length() > MAX_FAILURE_REASON_BYTES) {
            failureReason = failureReason.substring(0, MAX_FAILURE_REASON_BYTES) + "…[truncated]";
        }
    }

    /** Return a new summary with the given current stage. */
    public PipelineExecutionSummary withCurrentStage(String stage) {
        return new PipelineExecutionSummary(executionId, pipelineId, tenantId,
                startedAt, completedAt, status, stage, stageDurations, failureReason, totalDuration);
    }

    /** Return a new summary with an additional stage duration recorded. */
    public PipelineExecutionSummary withStageDuration(String stage, Duration duration) {
        Map<String, Duration> next = new LinkedHashMap<>(stageDurations);
        next.put(stage, duration);
        return new PipelineExecutionSummary(executionId, pipelineId, tenantId,
                startedAt, completedAt, status, currentStage, next, failureReason, totalDuration);
    }

    /** Return a new summary marked as successful. */
    public PipelineExecutionSummary completedSuccessfully(Instant when, Duration total) {
        return new PipelineExecutionSummary(executionId, pipelineId, tenantId,
                startedAt, when, ExecutionStatus.SUCCESS, null, stageDurations, failureReason, total);
    }

    /** Return a new summary marked as failed with the given reason. */
    public PipelineExecutionSummary completedWithFailure(Instant when, String reason, Duration total) {
        return new PipelineExecutionSummary(executionId, pipelineId, tenantId,
                startedAt, when, ExecutionStatus.FAILED, currentStage, stageDurations, reason, total);
    }
}
