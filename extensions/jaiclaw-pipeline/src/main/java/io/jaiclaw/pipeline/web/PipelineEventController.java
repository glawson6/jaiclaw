package io.jaiclaw.pipeline.web;

import io.jaiclaw.pipeline.PipelineProperties;
import io.jaiclaw.pipeline.PipelineRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Server-Sent Events endpoints for pipeline lifecycle streaming.
 *
 * <ul>
 *   <li>{@code GET /api/pipelines/{id}/events} — one pipeline's stream.
 *       Emits {@code snapshot} (definition + recent executions) on connect,
 *       then {@code execution-started}, {@code stage-started},
 *       {@code stage-completed}, {@code stage-failed},
 *       {@code execution-completed}, {@code execution-failed} for every
 *       execution of the named pipeline.</li>
 *   <li>{@code GET /api/pipelines/events} — whole-system stream.
 *       Same event names, but for every pipeline visible to the caller's
 *       tenant. The initial {@code snapshot} lists every registered
 *       pipeline id + name (definitions only, no history).</li>
 * </ul>
 *
 * <p>Per-tenant max-connections enforced by
 * {@link PipelineEventBroadcaster#register}; violations map to HTTP 429.
 * Unknown pipeline ids on the per-pipeline endpoint return 404.
 *
 * <p>The emitter timeout is controlled by
 * {@code jaiclaw.pipeline.sse.timeout-seconds} (default 300). Clients
 * should reconnect on browser-level errors via {@code EventSource}'s
 * built-in auto-retry.
 */
@RestController
@RequestMapping("${jaiclaw.pipeline.http-trigger.base-path:/api/pipelines}")
public class PipelineEventController {

    private final PipelineEventBroadcaster broadcaster;
    private final PipelineRegistry registry;
    private final PipelineProperties.SseProperties sseProperties;

    /**
     * Sole constructor — takes the full {@link PipelineProperties} and
     * pulls its {@code .sse()} sub-record. Spring's {@code @ConfigurationProperties}
     * infrastructure doesn't auto-register nested records as standalone
     * beans, so we can't ask Spring to inject the sub-record directly.
     */
    public PipelineEventController(PipelineEventBroadcaster broadcaster,
                                   PipelineRegistry registry,
                                   PipelineProperties properties) {
        this.broadcaster = broadcaster;
        this.registry = registry;
        this.sseProperties = properties.sse() != null
                ? properties.sse()
                : PipelineProperties.SseProperties.DEFAULT;
    }

    /**
     * Whole-system stream — every pipeline event for the caller's tenant.
     * The path segment {@code events} is safe because pipeline ids can't
     * collide (validated at registration time to reject reserved names).
     */
    @GetMapping("/events")
    public ResponseEntity<?> streamAll() {
        return openStream(PipelineEventBroadcaster.GLOBAL_PIPELINE_ID);
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<?> streamOne(@PathVariable("id") String pipelineId) {
        if (registry != null && registry.get(pipelineId) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "pipeline not found: " + pipelineId));
        }
        return openStream(pipelineId);
    }

    private ResponseEntity<?> openStream(String pipelineId) {
        SseEmitter emitter = new SseEmitter(sseProperties.timeoutSeconds() * 1000L);
        SseEmitter registered = broadcaster.register(pipelineId, emitter);
        if (registered == null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "error", "max SSE connections reached for tenant",
                            "hint", "lower the client polling rate or raise jaiclaw.pipeline.sse.max-connections"));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(registered);
    }
}
