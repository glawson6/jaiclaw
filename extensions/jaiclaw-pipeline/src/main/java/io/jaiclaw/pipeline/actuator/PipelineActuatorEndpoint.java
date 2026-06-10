package io.jaiclaw.pipeline.actuator;

import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring Boot Actuator endpoint exposing pipeline metadata and recent executions
 * under {@code /actuator/pipelines}.
 */
@Endpoint(id = "pipelines")
public class PipelineActuatorEndpoint {

    private final PipelineRegistry registry;
    private final PipelineExecutionTracker tracker;

    public PipelineActuatorEndpoint(PipelineRegistry registry, PipelineExecutionTracker tracker) {
        this.registry = registry;
        this.tracker = tracker;
    }

    /** {@code GET /actuator/pipelines} — list all registered pipelines. */
    @ReadOperation
    public Map<String, Object> list() {
        List<Map<String, Object>> pipelines = registry.getAll().stream()
                .map(PipelineActuatorEndpoint::summarize)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", pipelines.size());
        result.put("pipelines", pipelines);
        return result;
    }

    /** {@code GET /actuator/pipelines/{id}} — definition + recent executions. */
    @ReadOperation
    public Map<String, Object> byId(@Selector String id) {
        PipelineDefinition definition = registry.get(id);
        if (definition == null) {
            return Map.of("error", "Pipeline '" + id + "' not found");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("definition", summarize(definition));
        if (tracker != null) {
            result.put("recentExecutions", tracker.recent(id).stream()
                    .map(PipelineActuatorEndpoint::summarize)
                    .toList());
        } else {
            result.put("recentExecutions", List.of());
        }
        return result;
    }

    /** {@code GET /actuator/pipelines/{id}/{executionId}} — full execution detail. */
    @ReadOperation
    public Map<String, Object> executionById(@Selector String id, @Selector String executionId) {
        if (tracker == null) {
            return Map.of("error", "Execution tracker is disabled");
        }
        Optional<PipelineExecutionSummary> summary = tracker.byId(executionId);
        if (summary.isEmpty() || !id.equals(summary.get().pipelineId())) {
            return Map.of("error", "Execution '" + executionId + "' not found for pipeline '" + id + "'");
        }
        return summarize(summary.get());
    }

    private static Map<String, Object> summarize(PipelineDefinition definition) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", definition.id());
        map.put("name", definition.name());
        map.put("enabled", definition.enabled());
        map.put("trigger", definition.trigger() != null ? definition.trigger().type().name() : null);
        map.put("errorStrategy", definition.errorStrategy().name());
        map.put("stageCount", definition.stages().size());
        map.put("stageNames", definition.stages().stream().map(s -> s.name()).toList());
        return map;
    }

    private static Map<String, Object> summarize(PipelineExecutionSummary summary) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("executionId", summary.executionId());
        map.put("pipelineId", summary.pipelineId());
        map.put("tenantId", summary.tenantId());
        map.put("status", summary.status().name());
        map.put("startedAt", summary.startedAt());
        map.put("completedAt", summary.completedAt());
        map.put("currentStage", summary.currentStage());
        map.put("totalDurationMs", summary.totalDuration() != null ? summary.totalDuration().toMillis() : null);
        Map<String, Long> stageDurations = new LinkedHashMap<>();
        summary.stageDurations().forEach((name, dur) -> stageDurations.put(name, dur.toMillis()));
        map.put("stageDurationsMs", stageDurations);
        map.put("failureReason", summary.failureReason());
        return map;
    }
}
