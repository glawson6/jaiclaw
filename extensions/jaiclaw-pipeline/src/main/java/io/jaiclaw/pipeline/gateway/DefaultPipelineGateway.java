package io.jaiclaw.pipeline.gateway;

import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.PipelineRegistry;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Default {@link PipelineGateway} that sends bodies to the
 * {@code direct:pipeline-<id>} convergence route registered by
 * {@code PipelineRouteBuilder}.
 *
 * <p>The send is asynchronous — the gateway returns the
 * {@link PipelineExecutionHandle} immediately so callers don't block on the
 * pipeline finishing. Execution state can be polled via the
 * {@code /actuator/pipelines/{id}/{executionId}} endpoint.
 */
public class DefaultPipelineGateway implements PipelineGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultPipelineGateway.class);

    /** Header read by the trigger route to seed the correlation ID. */
    public static final String HEADER_CORRELATION_ID = "JaiClawPipelineCorrelationId";

    /** Header read by the trigger route to seed the tenant ID. */
    public static final String HEADER_TENANT_ID = "JaiClawPipelineTenantId";

    /** Header carrying the externally-generated execution ID for caller-side correlation. */
    public static final String HEADER_GATEWAY_EXECUTION_ID = "JaiClawPipelineGatewayExecutionId";

    private final ProducerTemplate producerTemplate;
    private final PipelineRegistry registry;

    public DefaultPipelineGateway(ProducerTemplate producerTemplate, PipelineRegistry registry) {
        this.producerTemplate = producerTemplate;
        this.registry = registry;
    }

    @Override
    public PipelineExecutionHandle trigger(String pipelineId, String input) {
        return trigger(pipelineId, input, null, null);
    }

    @Override
    public PipelineExecutionHandle trigger(String pipelineId, String input, String tenantId) {
        return trigger(pipelineId, input, tenantId, null);
    }

    @Override
    public PipelineExecutionHandle trigger(String pipelineId, String input, String tenantId, String correlationId) {
        PipelineDefinition definition = registry.get(pipelineId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown pipeline: '" + pipelineId + "'");
        }
        if (!definition.enabled()) {
            throw new IllegalArgumentException("Pipeline '" + pipelineId + "' is disabled");
        }

        String executionId = UUID.randomUUID().toString();
        String uri = "direct:pipeline-" + pipelineId;
        log.debug("Submitting execution {} to {}", executionId, uri);

        producerTemplate.asyncSend(uri, exchange -> {
            exchange.getIn().setBody(input);
            exchange.getIn().setHeader(HEADER_GATEWAY_EXECUTION_ID, executionId);
            if (tenantId != null && !tenantId.isBlank()) {
                exchange.getIn().setHeader(HEADER_TENANT_ID, tenantId);
            }
            if (correlationId != null && !correlationId.isBlank()) {
                exchange.getIn().setHeader(HEADER_CORRELATION_ID, correlationId);
            }
        });
        return new PipelineExecutionHandle(executionId, pipelineId, Instant.now());
    }
}
