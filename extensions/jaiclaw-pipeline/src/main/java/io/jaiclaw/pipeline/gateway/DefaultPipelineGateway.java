package io.jaiclaw.pipeline.gateway;

import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.PipelineRegistry;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Default {@link PipelineGateway} that sends bodies to the
 * {@code direct:pipeline-<id>} convergence route registered by
 * {@code PipelineRouteBuilder}.
 *
 * <p>Two submission flavors:
 * <ul>
 *   <li>{@link #trigger(String, String)} — fire-and-forget. The send is
 *       asynchronous; the {@link PipelineExecutionHandle} returns immediately.
 *       Execution state can be polled via the
 *       {@code /actuator/pipelines/{id}/{executionId}} endpoint.</li>
 *   <li>{@link #triggerAsync(String, String)} — registers a future with the
 *       {@link PipelineSyncCoordinator} <i>before</i> submission, sets the
 *       {@link #HEADER_SYNC_REQUESTED} marker on the exchange, and returns the
 *       future. The route completes it when the output / exception handler
 *       runs.</li>
 * </ul>
 */
public class DefaultPipelineGateway implements PipelineGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultPipelineGateway.class);

    /** Header read by the trigger route to seed the correlation ID. */
    public static final String HEADER_CORRELATION_ID = "JaiClawPipelineCorrelationId";

    /** Header read by the trigger route to seed the tenant ID. */
    public static final String HEADER_TENANT_ID = "JaiClawPipelineTenantId";

    /** Header carrying the externally-generated execution ID for caller-side correlation. */
    public static final String HEADER_GATEWAY_EXECUTION_ID = "JaiClawPipelineGatewayExecutionId";

    /**
     * Marker header set when the caller used {@link #triggerAsync(String, String)}.
     * Read by the route's output / exception handlers to decide whether to
     * complete the registered future.
     */
    public static final String HEADER_SYNC_REQUESTED = "JaiClawPipelineSyncRequested";

    private final ProducerTemplate producerTemplate;
    private final PipelineRegistry registry;
    private final PipelineSyncCoordinator syncCoordinator;

    /** Convenience constructor for fire-and-forget-only deployments (tests / minimal setups). */
    public DefaultPipelineGateway(ProducerTemplate producerTemplate, PipelineRegistry registry) {
        this(producerTemplate, registry, null);
    }

    public DefaultPipelineGateway(
            ProducerTemplate producerTemplate,
            PipelineRegistry registry,
            PipelineSyncCoordinator syncCoordinator) {
        this.producerTemplate = producerTemplate;
        this.registry = registry;
        this.syncCoordinator = syncCoordinator;
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
        validatePipeline(pipelineId);
        String executionId = UUID.randomUUID().toString();
        submit(pipelineId, input, tenantId, correlationId, executionId, false);
        return new PipelineExecutionHandle(executionId, pipelineId, Instant.now());
    }

    @Override
    public CompletableFuture<PipelineExecutionResult> triggerAsync(String pipelineId, String input) {
        return triggerAsync(pipelineId, input, null, null);
    }

    @Override
    public CompletableFuture<PipelineExecutionResult> triggerAsync(
            String pipelineId, String input, String tenantId) {
        return triggerAsync(pipelineId, input, tenantId, null);
    }

    @Override
    public CompletableFuture<PipelineExecutionResult> triggerAsync(
            String pipelineId, String input, String tenantId, String correlationId) {
        if (syncCoordinator == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "PipelineSyncCoordinator not configured — triggerAsync requires the coordinator bean."
                            + " Use trigger(...) for fire-and-forget submission."));
        }
        try {
            validatePipeline(pipelineId);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        String executionId = UUID.randomUUID().toString();
        CompletableFuture<PipelineExecutionResult> future = syncCoordinator.register(executionId);
        if (future.isCompletedExceptionally()) {
            // Capacity rejection — do NOT submit to Camel.
            return future;
        }
        try {
            submit(pipelineId, input, tenantId, correlationId, executionId, true);
        } catch (RuntimeException e) {
            // Submission failed before any route ran — complete the future now
            // rather than leaving the caller blocked until the orphan sweep.
            syncCoordinator.completeExceptionally(executionId, e);
            return future;
        }
        return future;
    }

    private void validatePipeline(String pipelineId) {
        PipelineDefinition definition = registry.get(pipelineId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown pipeline: '" + pipelineId + "'");
        }
        if (!definition.enabled()) {
            throw new IllegalArgumentException("Pipeline '" + pipelineId + "' is disabled");
        }
    }

    private void submit(
            String pipelineId,
            String input,
            String tenantId,
            String correlationId,
            String executionId,
            boolean syncRequested) {
        String uri = "direct:pipeline-" + pipelineId;
        log.debug("Submitting execution {} to {} (sync={})", executionId, uri, syncRequested);
        producerTemplate.asyncSend(uri, exchange -> {
            exchange.getIn().setBody(input);
            exchange.getIn().setHeader(HEADER_GATEWAY_EXECUTION_ID, executionId);
            if (tenantId != null && !tenantId.isBlank()) {
                exchange.getIn().setHeader(HEADER_TENANT_ID, tenantId);
            }
            if (correlationId != null && !correlationId.isBlank()) {
                exchange.getIn().setHeader(HEADER_CORRELATION_ID, correlationId);
            }
            if (syncRequested) {
                exchange.getIn().setHeader(HEADER_SYNC_REQUESTED, Boolean.TRUE);
            }
        });
    }
}
