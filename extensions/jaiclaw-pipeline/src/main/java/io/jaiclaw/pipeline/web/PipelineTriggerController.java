package io.jaiclaw.pipeline.web;

import io.jaiclaw.pipeline.PipelineProperties;
import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST entrypoint for triggering pipelines over HTTP.
 *
 * <p>The surface is deliberately opaque — the API consumer never sees an
 * internal pipeline id. {@code POST /api/pipelines/trigger} accepts a
 * {@link PipelineTriggerRequest} that carries a logical pipeline alias.
 * The framework resolves the alias via
 * {@code jaiclaw.pipeline.http-trigger.allowed} (operator-managed
 * alias → internal pipeline id map). Unknown aliases produce 404; the
 * existing
 * {@code POST /{id}/trigger} endpoint has been removed to keep all
 * pipeline-id selection inside the request body where path tampering
 * can't reach it.
 *
 * <p>{@code GET /api/pipelines/status/{executionId}} returns a small
 * consumer-safe {@link StatusBody} (no {@code pipelineId},
 * {@code tenantId}, or stage-internal data). Operators who need the full
 * record use the actuator endpoint.
 *
 * <p>Authentication and authorization are delegated to the Spring
 * Security filter chain — this controller does no in-method auth
 * checks.
 */
@RestController
@RequestMapping("${jaiclaw.pipeline.http-trigger.base-path:/api/pipelines}")
public class PipelineTriggerController {

    private static final Logger log = LoggerFactory.getLogger(PipelineTriggerController.class);

    private final PipelineGateway gateway;
    private final PipelineProperties properties;
    private final ObjectProvider<PipelineExecutionTracker> trackerProvider;

    public PipelineTriggerController(
            PipelineGateway gateway,
            PipelineProperties properties,
            ObjectProvider<PipelineExecutionTracker> trackerProvider) {
        this.gateway = gateway;
        this.properties = properties;
        this.trackerProvider = trackerProvider;
    }

    @PostMapping("/trigger")
    public ResponseEntity<?> trigger(
            @RequestBody(required = false) PipelineTriggerRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String corrIdHeader) {

        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(new ErrorBody("Request body is required"));
        }
        String alias = request.pipeline();
        if (alias == null || alias.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorBody("'pipeline' field is required"));
        }
        Map<String, String> allowed = properties.httpTrigger().allowed();
        String pipelineId = allowed.get(alias);
        if (pipelineId == null) {
            log.warn("Rejected unknown pipeline alias '{}' — allowed aliases: {}",
                    alias, allowed.keySet());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorBody("Unknown pipeline: " + alias));
        }

        String tenantId      = firstNonBlank(tenantIdHeader, request.tenantId());
        String correlationId = firstNonBlank(corrIdHeader,   request.correlationId());

        if (!request.metadata().isEmpty()) {
            // v1 accepts the field for forward compatibility but doesn't yet
            // thread it into PipelineContext.metadata() — the gateway signature
            // would need to grow. Log so operators can see what arrived.
            log.info("Pipeline trigger metadata received (not yet propagated to PipelineContext): {}",
                    request.metadata());
        }

        try {
            PipelineExecutionHandle handle = gateway.trigger(
                    pipelineId, request.payload(), tenantId, correlationId);
            log.info("Pipeline trigger — alias={} pipeline={} executionId={}",
                    alias, pipelineId, handle.executionId());
            return ResponseEntity.accepted()
                    .body(new TriggerResponse(handle.executionId(), handle.submittedAt()));
        } catch (IllegalArgumentException ex) {
            // Defensive: operator mapped an alias to a non-existent pipeline.
            // Surface as 500 (server misconfiguration) rather than 404 — the
            // caller's request is valid; the deployment is wrong.
            log.error("Alias '{}' maps to non-existent pipeline '{}': {}",
                    alias, pipelineId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorBody("Pipeline misconfigured for alias: " + alias));
        }
    }

    @GetMapping("/status/{executionId}")
    public ResponseEntity<?> status(@PathVariable("executionId") String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorBody("'executionId' must not be blank"));
        }
        PipelineExecutionTracker tracker = trackerProvider.getIfAvailable();
        if (tracker == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorBody(
                            "Execution tracking is not enabled — set "
                                    + "jaiclaw.pipeline.tracker.enabled=true to expose status"));
        }
        return tracker.byId(executionId)
                .<ResponseEntity<?>>map(summary -> ResponseEntity.ok(toStatusBody(summary)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorBody("No execution found for id: " + executionId)));
    }

    private static StatusBody toStatusBody(PipelineExecutionSummary summary) {
        return new StatusBody(
                summary.executionId(),
                summary.status() == null ? null : summary.status().name(),
                summary.startedAt(),
                summary.completedAt(),
                summary.failureReason());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    public record ErrorBody(String error) {}
}
