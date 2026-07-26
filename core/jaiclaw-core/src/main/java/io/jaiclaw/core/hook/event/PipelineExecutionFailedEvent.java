package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired by the pipeline extension when a pipeline execution terminates
 * with an unhandled exception (either a stage failure that the error
 * strategy didn't recover from, or a framework-level fault).
 *
 * <p>{@link HookEvent#agentId()} carries the {@code pipelineId};
 * {@link HookEvent#sessionKey()} carries the {@code executionId}.
 *
 * @param agentId          pipeline id
 * @param sessionKey       execution id
 * @param timestamp        when the execution failed
 * @param pipelineId       same as {@link #agentId()}
 * @param executionId      same as {@link #sessionKey()}
 * @param tenantId         tenant this execution belonged to
 * @param failedStage      name of the stage where the failure originated; {@code null} if outside a stage
 * @param failureReason    human-readable message (truncated to a safe length by the firer)
 * @param totalDurationMs  wall-clock duration from start to failure, in milliseconds
 */
@Experimental
public record PipelineExecutionFailedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String pipelineId,
        String executionId,
        String tenantId,
        String failedStage,
        String failureReason,
        long totalDurationMs
) implements HookEvent {

    public static PipelineExecutionFailedEvent of(String pipelineId, String executionId,
                                                   String tenantId,
                                                   String failedStage, String failureReason,
                                                   long totalDurationMs) {
        return new PipelineExecutionFailedEvent(
                pipelineId, executionId, Instant.now(),
                pipelineId, executionId, tenantId, failedStage, failureReason, totalDurationMs);
    }
}
