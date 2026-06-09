package io.jaiclaw.pipeline;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer metrics for pipeline execution. Only instantiated when
 * Micrometer is on the classpath.
 */
public class PipelineMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicInteger> activeGauges = new ConcurrentHashMap<>();

    public PipelineMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Record a complete pipeline execution.
     */
    public void recordPipelineExecution(String pipelineId, String tenantId, boolean success, Duration duration) {
        Tags tags = Tags.of(
                Tag.of("pipelineId", pipelineId),
                Tag.of("tenantId", tenantId != null ? tenantId : "default"),
                Tag.of("outcome", success ? "success" : "failure")
        );
        Timer.builder("jaiclaw.pipeline.executions")
                .tags(tags)
                .register(registry)
                .record(duration);
    }

    /**
     * Record a single stage execution.
     */
    public void recordStageExecution(String pipelineId, String stageName, String stageType,
                                      boolean success, Duration duration) {
        Tags tags = Tags.of(
                Tag.of("pipelineId", pipelineId),
                Tag.of("stageName", stageName),
                Tag.of("stageType", stageType),
                Tag.of("outcome", success ? "success" : "failure")
        );
        Timer.builder("jaiclaw.pipeline.stage.duration")
                .tags(tags)
                .register(registry)
                .record(duration);
    }

    /**
     * Increment/decrement active pipeline gauge.
     *
     * @param pipelineId the pipeline ID
     * @param delta      +1 on start, -1 on end
     */
    public void recordPipelineActive(String pipelineId, int delta) {
        AtomicInteger gauge = activeGauges.computeIfAbsent(pipelineId, id -> {
            AtomicInteger counter = new AtomicInteger(0);
            registry.gauge("jaiclaw.pipeline.active",
                    Tags.of(Tag.of("pipelineId", id)),
                    counter);
            return counter;
        });
        gauge.addAndGet(delta);
    }
}
