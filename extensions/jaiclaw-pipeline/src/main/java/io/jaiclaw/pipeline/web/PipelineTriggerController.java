package io.jaiclaw.pipeline.web;

import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entrypoint for triggering pipelines over HTTP.
 *
 * <p>{@code POST /api/pipelines/{id}/trigger} (path configurable via
 * {@code jaiclaw.pipeline.http-trigger.base-path}) submits the request body
 * to the {@link PipelineGateway} and returns a {@link PipelineExecutionHandle}
 * with HTTP 202 Accepted. Execution status is queryable via the Actuator
 * endpoint registered under {@code /actuator/pipelines}.
 *
 * <p>Authentication and authorization are delegated to the Spring Security
 * filter chain — this controller does no in-method auth checks.
 */
@RestController
@RequestMapping("${jaiclaw.pipeline.http-trigger.base-path:/api/pipelines}")
public class PipelineTriggerController {

    private final PipelineGateway gateway;

    public PipelineTriggerController(PipelineGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<?> trigger(
            @PathVariable("id") String pipelineId,
            @RequestBody(required = false) String body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        try {
            PipelineExecutionHandle handle = gateway.trigger(pipelineId, body, tenantId, correlationId);
            return ResponseEntity.accepted().body(handle);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody(ex.getMessage()));
        }
    }

    public record ErrorBody(String error) {}
}
