package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired by the pipeline extension when a single stage throws an unhandled
 * exception. Independent of {@link PipelineExecutionFailedEvent} — a stage
 * failure may be recovered by the pipeline's error strategy (e.g.
 * {@code CONTINUE} or {@code DEAD_LETTER}) without terminating the
 * execution, in which case only this stage event fires.
 *
 * <p>{@link HookEvent#agentId()} carries the {@code pipelineId};
 * {@link HookEvent#sessionKey()} carries the {@code executionId}.
 *
 * @param agentId        pipeline id
 * @param sessionKey     execution id
 * @param timestamp      when the stage failed
 * @param pipelineId     same as {@link #agentId()}
 * @param executionId    same as {@link #sessionKey()}
 * @param tenantId       tenant this execution belongs to
 * @param stageName      the failing stage's name
 * @param stageIndex     zero-based position in the pipeline's stages
 * @param failureReason  human-readable message (truncated by the firer)
 */
@Experimental
public record PipelineStageFailedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String pipelineId,
        String executionId,
        String tenantId,
        String stageName,
        int stageIndex,
        String failureReason
) implements HookEvent {

    public static PipelineStageFailedEvent of(String pipelineId, String executionId,
                                               String tenantId,
                                               String stageName, int stageIndex,
                                               String failureReason) {
        return new PipelineStageFailedEvent(
                pipelineId, executionId, Instant.now(),
                pipelineId, executionId, tenantId, stageName, stageIndex, failureReason);
    }
}
