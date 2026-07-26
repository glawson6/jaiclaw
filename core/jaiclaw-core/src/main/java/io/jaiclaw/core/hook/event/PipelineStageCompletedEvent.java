package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired by the pipeline extension after each stage completes successfully.
 * Complements the mapped {@link ToolCallEndedEvent} the legacy firer emits.
 *
 * <p>{@link HookEvent#agentId()} carries the {@code pipelineId};
 * {@link HookEvent#sessionKey()} carries the {@code executionId}.
 *
 * <p>{@link #resultPreview()} is a size-capped preview of the stage's
 * output (default 200 chars, truncated in the firer). Consumers wanting
 * the full output should read
 * {@code PipelineExecutionSummary.stageOutputs()} via the execution
 * tracker — the event stream is deliberately not a full data channel.
 *
 * @param agentId       pipeline id
 * @param sessionKey    execution id
 * @param timestamp     when the stage completed
 * @param pipelineId    same as {@link #agentId()}
 * @param executionId   same as {@link #sessionKey()}
 * @param tenantId      tenant this execution belongs to
 * @param stageName     the stage's unique name
 * @param stageType     the stage type discriminator
 * @param stageIndex    zero-based position in the pipeline's stages
 * @param resultPreview truncated preview of the stage output
 * @param durationMs    stage wall-clock duration in milliseconds
 */
@Experimental
public record PipelineStageCompletedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String pipelineId,
        String executionId,
        String tenantId,
        String stageName,
        String stageType,
        int stageIndex,
        String resultPreview,
        long durationMs
) implements HookEvent {

    public static PipelineStageCompletedEvent of(String pipelineId, String executionId,
                                                  String tenantId,
                                                  String stageName, String stageType,
                                                  int stageIndex,
                                                  String resultPreview, long durationMs) {
        return new PipelineStageCompletedEvent(
                pipelineId, executionId, Instant.now(),
                pipelineId, executionId, tenantId,
                stageName, stageType, stageIndex, resultPreview, durationMs);
    }
}
