package io.jaiclaw.pipeline.gateway;

import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.tracking.ExecutionStatus;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Snapshot of a pipeline execution at completion. Returned by
 * {@link PipelineGateway#triggerAsync(String, String)} (wrapped in a
 * {@link java.util.concurrent.CompletableFuture}) and
 * {@link PipelineGateway#triggerAndAwait(String, String, Duration)}.
 *
 * <p>Stage failures complete normally with {@link ExecutionStatus#FAILED} and
 * a populated {@code failureReason}. Only true timeouts and infrastructure
 * faults (coordinator capacity, orphan reaping) surface as exceptional
 * future completions.
 *
 * @param executionId      unique execution UUID (matches
 *                         {@link PipelineExecutionHandle#executionId()} from the
 *                         fire-and-forget API)
 * @param pipelineId       pipeline definition id
 * @param tenantId         tenant id (nullable for single-tenant)
 * @param correlationId    distributed-tracing correlation id (nullable)
 * @param totalStages      total stage count for this pipeline
 * @param finalStageIndex  stage index at completion — equals {@code totalStages}
 *                         on SUCCESS, the failed stage's index on FAILED
 * @param stageOutputs     stage name → output text, insertion-ordered
 * @param input            original trigger payload (nullable; truncated to
 *                         {@link PipelineContext#MAX_INPUT_BYTES} by the route)
 * @param status           {@link ExecutionStatus#SUCCESS} or
 *                         {@link ExecutionStatus#FAILED} — {@code RUNNING} is
 *                         rejected
 * @param submittedAt      gateway-clock instant at which the trigger was accepted
 * @param completedAt      server-clock instant at completion
 * @param totalDuration    wall-clock duration from start of route to completion
 * @param failureReason    failure message for FAILED status, truncated to
 *                         {@link PipelineExecutionSummary#MAX_FAILURE_REASON_BYTES}
 *                         (nullable on SUCCESS, required on FAILED)
 */
public record PipelineExecutionResult(
        String executionId,
        String pipelineId,
        String tenantId,
        String correlationId,
        int totalStages,
        int finalStageIndex,
        Map<String, String> stageOutputs,
        String input,
        ExecutionStatus status,
        Instant submittedAt,
        Instant completedAt,
        Duration totalDuration,
        String failureReason
) {
    public PipelineExecutionResult {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("pipelineId must not be blank");
        }
        if (status == null || status == ExecutionStatus.RUNNING) {
            throw new IllegalArgumentException("status must be SUCCESS or FAILED, got: " + status);
        }
        if (status == ExecutionStatus.FAILED && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("failureReason required when status=FAILED");
        }
        if (stageOutputs == null) {
            stageOutputs = Map.of();
        } else {
            stageOutputs = Map.copyOf(stageOutputs);
        }
        if (submittedAt == null) submittedAt = Instant.now();
        if (completedAt == null) completedAt = Instant.now();
        if (totalDuration == null) totalDuration = Duration.ZERO;
        if (failureReason != null
                && failureReason.length() > PipelineExecutionSummary.MAX_FAILURE_REASON_BYTES) {
            failureReason = failureReason.substring(
                    0, PipelineExecutionSummary.MAX_FAILURE_REASON_BYTES) + "…[truncated]";
        }
    }

    /** Build a SUCCESS result from a completed {@link PipelineContext}. */
    public static PipelineExecutionResult success(
            PipelineContext ctx,
            Instant submittedAt,
            Instant completedAt,
            Duration totalDuration) {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx must not be null");
        }
        return new PipelineExecutionResult(
                ctx.executionId(),
                ctx.pipelineId(),
                ctx.tenantId(),
                ctx.correlationId(),
                ctx.totalStages(),
                ctx.stageIndex(),
                flattenStageOutputs(ctx),
                extractInput(ctx),
                ExecutionStatus.SUCCESS,
                submittedAt,
                completedAt,
                totalDuration,
                null
        );
    }

    /** Build a FAILED result from a {@link PipelineContext} at the point of failure. */
    public static PipelineExecutionResult failure(
            PipelineContext ctx,
            String reason,
            Instant submittedAt,
            Instant completedAt,
            Duration totalDuration) {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx must not be null");
        }
        String safeReason = (reason == null || reason.isBlank()) ? "unknown failure" : reason;
        return new PipelineExecutionResult(
                ctx.executionId(),
                ctx.pipelineId(),
                ctx.tenantId(),
                ctx.correlationId(),
                ctx.totalStages(),
                ctx.stageIndex(),
                flattenStageOutputs(ctx),
                extractInput(ctx),
                ExecutionStatus.FAILED,
                submittedAt,
                completedAt,
                totalDuration,
                safeReason
        );
    }

    private static Map<String, String> flattenStageOutputs(PipelineContext ctx) {
        Map<String, PipelineContext.StageOutput> raw = ctx.stageOutputs();
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, String> flat = new LinkedHashMap<>();
        for (Map.Entry<String, PipelineContext.StageOutput> entry : raw.entrySet()) {
            PipelineContext.StageOutput out = entry.getValue();
            flat.put(entry.getKey(), out == null || out.output() == null ? "" : out.output());
        }
        return flat;
    }

    private static String extractInput(PipelineContext ctx) {
        Map<String, String> metadata = ctx.metadata();
        if (metadata == null) return null;
        return metadata.get(PipelineContext.INPUT_METADATA_KEY);
    }
}
