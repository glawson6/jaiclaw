package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired by the pipeline extension before each stage begins executing.
 * Complements the mapped {@link ToolCallStartedEvent} the legacy firer
 * emits (each stage is reported as a tool call to keep agent-oriented
 * observability plugins working).
 *
 * <p>{@link HookEvent#agentId()} carries the {@code pipelineId};
 * {@link HookEvent#sessionKey()} carries the {@code executionId}.
 *
 * @param agentId     pipeline id
 * @param sessionKey  execution id
 * @param timestamp   when the stage started
 * @param pipelineId  same as {@link #agentId()}
 * @param executionId same as {@link #sessionKey()}
 * @param tenantId    tenant this execution belongs to
 * @param stageName   the stage's unique name within the pipeline
 * @param stageType   the stage type discriminator ({@code AGENT}, {@code PROCESSOR}, {@code CAMEL})
 * @param stageIndex  zero-based position in the pipeline's ordered stage list
 */
@Experimental
public record PipelineStageStartedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String pipelineId,
        String executionId,
        String tenantId,
        String stageName,
        String stageType,
        int stageIndex
) implements HookEvent {

    public static PipelineStageStartedEvent of(String pipelineId, String executionId,
                                                String tenantId,
                                                String stageName, String stageType,
                                                int stageIndex) {
        return new PipelineStageStartedEvent(
                pipelineId, executionId, Instant.now(),
                pipelineId, executionId, tenantId, stageName, stageType, stageIndex);
    }
}
