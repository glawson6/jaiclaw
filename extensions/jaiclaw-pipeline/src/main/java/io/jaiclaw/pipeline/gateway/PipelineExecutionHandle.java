package io.jaiclaw.pipeline.gateway;

import java.time.Instant;

/**
 * Correlation token returned by {@link PipelineGateway#trigger(String, String)}.
 * Callers can look the execution up in the
 * {@code /actuator/pipelines/{id}/{executionId}} endpoint while it runs.
 *
 * @param executionId unique UUID assigned at submission time
 * @param pipelineId  the pipeline that was triggered
 * @param submittedAt server-clock instant at which the gateway accepted the request
 */
public record PipelineExecutionHandle(
        String executionId,
        String pipelineId,
        Instant submittedAt
) {
    public PipelineExecutionHandle {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("pipelineId must not be blank");
        }
        if (submittedAt == null) submittedAt = Instant.now();
    }
}
