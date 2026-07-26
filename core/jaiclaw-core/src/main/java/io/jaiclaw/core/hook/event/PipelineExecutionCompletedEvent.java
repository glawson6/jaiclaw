package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired by the pipeline extension when a pipeline execution finishes
 * successfully (all stages ran without an unhandled exception). Complements
 * the mapped {@link AgentEndedEvent} the legacy firer emits.
 *
 * <p>{@link HookEvent#agentId()} carries the {@code pipelineId};
 * {@link HookEvent#sessionKey()} carries the {@code executionId}.
 *
 * @param agentId          pipeline id
 * @param sessionKey       execution id
 * @param timestamp        when the execution completed
 * @param pipelineId       same as {@link #agentId()}
 * @param executionId      same as {@link #sessionKey()}
 * @param tenantId         tenant this execution belonged to; {@code null} in single-tenant mode
 * @param totalStages      number of stages the pipeline had
 * @param totalDurationMs  wall-clock duration from start to completion, in milliseconds
 * @param result           caller-visible result string (nullable). Populated by
 *                         the runtime from a stage's {@code metadata.__result__},
 *                         the definition's {@code resultTemplate}, or the
 *                         fallback {@code "SUCCESS"}. Same string that lands on
 *                         {@code PipelineExecutionSummary.result} + the
 *                         controller's {@code StatusBody.result}.
 */
@Experimental
public record PipelineExecutionCompletedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String pipelineId,
        String executionId,
        String tenantId,
        int totalStages,
        long totalDurationMs,
        String result
) implements HookEvent {

    /**
     * Backward-compatible 5-arg factory with no result — delegates to the
     * 6-arg factory with {@code result = null}.
     */
    public static PipelineExecutionCompletedEvent of(String pipelineId, String executionId,
                                                     String tenantId,
                                                     int totalStages, long totalDurationMs) {
        return of(pipelineId, executionId, tenantId, totalStages, totalDurationMs, null);
    }

    /** Factory carrying the runtime-resolved result string. */
    public static PipelineExecutionCompletedEvent of(String pipelineId, String executionId,
                                                     String tenantId,
                                                     int totalStages, long totalDurationMs,
                                                     String result) {
        return new PipelineExecutionCompletedEvent(
                pipelineId, executionId, Instant.now(),
                pipelineId, executionId, tenantId, totalStages, totalDurationMs, result);
    }
}
