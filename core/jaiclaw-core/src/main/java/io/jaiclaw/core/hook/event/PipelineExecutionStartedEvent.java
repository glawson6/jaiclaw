package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired by the pipeline extension when a pipeline execution begins — before
 * any stage runs. Complements the mapped {@link AgentStartedEvent} the
 * legacy firer emits, so observability plugins that filter on agent events
 * still see pipeline activity while UI-shaped consumers get a dedicated,
 * pipeline-typed channel.
 *
 * <p>{@link HookEvent#agentId()} carries the {@code pipelineId};
 * {@link HookEvent#sessionKey()} carries the {@code executionId}. Both
 * conventions match the mapped firer's field usage for continuity.
 *
 * @param agentId     pipeline id (also surfaced as {@link #pipelineId()})
 * @param sessionKey  execution id (also surfaced as {@link #executionId()})
 * @param timestamp   when the execution started
 * @param pipelineId  same as {@link #agentId()}, named for clarity
 * @param executionId same as {@link #sessionKey()}, named for clarity
 * @param tenantId    tenant this execution belongs to; {@code null} in single-tenant mode
 */
@Experimental
public record PipelineExecutionStartedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String pipelineId,
        String executionId,
        String tenantId
) implements HookEvent {

    public static PipelineExecutionStartedEvent of(String pipelineId, String executionId,
                                                    String tenantId) {
        return new PipelineExecutionStartedEvent(
                pipelineId, executionId, Instant.now(),
                pipelineId, executionId, tenantId);
    }
}
