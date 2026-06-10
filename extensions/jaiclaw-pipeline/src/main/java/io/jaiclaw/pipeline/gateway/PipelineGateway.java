package io.jaiclaw.pipeline.gateway;

/**
 * Programmatic entrypoint for triggering pipelines by ID. Hides the internal
 * Camel {@code direct:pipeline-<id>} URI scheme so callers don't depend on
 * route-naming implementation details.
 */
public interface PipelineGateway {

    /** Trigger a pipeline with the given input body. */
    PipelineExecutionHandle trigger(String pipelineId, String input);

    /** Trigger a pipeline with input and an explicit tenant ID. */
    PipelineExecutionHandle trigger(String pipelineId, String input, String tenantId);

    /** Trigger a pipeline with input, tenant ID, and correlation ID. */
    PipelineExecutionHandle trigger(String pipelineId, String input, String tenantId, String correlationId);
}
