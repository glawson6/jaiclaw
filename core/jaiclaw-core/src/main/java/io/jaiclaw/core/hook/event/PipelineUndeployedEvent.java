package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired by the Pipeline Studio's lifecycle manager when a pipeline is
 * hot-undeployed at runtime (see
 * {@code docs/dev/pipeline/PIPELINE_HOT_RELOAD.md}). Also fires as
 * part of a redeploy (with {@code reason = "redeploy"}).
 *
 * <p>{@link HookEvent#agentId()} carries the {@code pipelineId};
 * {@link HookEvent#sessionKey()} is empty.
 *
 * @param agentId    pipeline id
 * @param sessionKey empty string
 * @param timestamp  when the undeploy completed
 * @param pipelineId same as {@link #agentId()}
 * @param tenantId   tenant that owned the definition
 * @param reason     lifecycle reason ({@code undeploy}, {@code redeploy}, {@code reset})
 */
@Experimental
public record PipelineUndeployedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String pipelineId,
        String tenantId,
        String reason
) implements HookEvent {

    public static PipelineUndeployedEvent of(String pipelineId,
                                             String tenantId,
                                             String reason) {
        return new PipelineUndeployedEvent(
                pipelineId, "", Instant.now(),
                pipelineId, tenantId, reason == null ? "undeploy" : reason);
    }
}
