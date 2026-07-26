package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired by the Pipeline Studio's lifecycle manager when a pipeline is
 * hot-deployed at runtime (see
 * {@code docs/dev/pipeline/PIPELINE_HOT_RELOAD.md}). Also fires as
 * part of the "undeploy old + deploy new" sequence in a redeploy.
 *
 * <p>{@link HookEvent#agentId()} carries the {@code pipelineId};
 * {@link HookEvent#sessionKey()} is empty (deploy events are not
 * per-execution).
 *
 * @param agentId    pipeline id
 * @param sessionKey empty string
 * @param timestamp  when the deploy completed
 * @param pipelineId same as {@link #agentId()}
 * @param tenantId   tenant that owns the definition (nullable in single-tenant)
 * @param stageCount number of stages the deployed definition has
 * @param origin     where the definition came from ({@code STUDIO},
 *                   {@code YAML_IMPORT}, or {@code CODE_BEAN})
 */
@Experimental
public record PipelineDeployedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String pipelineId,
        String tenantId,
        int stageCount,
        String origin
) implements HookEvent {

    public static PipelineDeployedEvent of(String pipelineId,
                                           String tenantId,
                                           int stageCount,
                                           String origin) {
        return new PipelineDeployedEvent(
                pipelineId, "", Instant.now(),
                pipelineId, tenantId, stageCount, origin);
    }
}
