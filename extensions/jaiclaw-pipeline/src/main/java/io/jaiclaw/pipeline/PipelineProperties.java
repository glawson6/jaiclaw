package io.jaiclaw.pipeline;

import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for the pipeline module.
 *
 * <p>Opt-in: the module no-ops unless {@code jaiclaw.pipeline.enabled=true}.
 * When enabled, at least one definition source must be configured:
 * inline {@code pipelines[]}, per-file {@code locations[]}, or a
 * {@code JaiClawPipeline} code bean. The auto-config fails startup
 * otherwise.
 *
 * @param enabled     master switch (default: false)
 * @param pipelines   YAML-defined pipeline definitions
 * @param defaults    global defaults for inter-stage transport
 * @param security    global security settings (per-pipeline overrides take precedence)
 * @param tracker     execution-tracker settings (recent history visible via Actuator)
 * @param httpTrigger HTTP trigger endpoint settings
 * @param locations   per-file pipeline location patterns (classpath / filesystem)
 */
@ConfigurationProperties(prefix = "jaiclaw.pipeline")
public record PipelineProperties(
        boolean enabled,
        List<PipelineDefinition> pipelines,
        PipelineDefaults defaults,
        PipelineSecurityProperties security,
        TrackerProperties tracker,
        HttpTriggerProperties httpTrigger,
        LocationProperties locations
) {
    /**
     * Default SEDA queue configuration for inter-stage transport.
     *
     * @param sedaSize            SEDA queue buffer size (default: 100)
     * @param concurrentConsumers number of concurrent consumers per queue (default: 5)
     * @param blockWhenFull       whether to block producers when the queue is full (default: true)
     * @param deadLetterUri       global fallback Camel URI for pipelines using
     *                            {@code errorStrategy=DEAD_LETTER} without an explicit
     *                            {@code deadLetterUri} (nullable)
     */
    public record PipelineDefaults(
            int sedaSize,
            int concurrentConsumers,
            boolean blockWhenFull,
            String deadLetterUri
    ) {
        public static final PipelineDefaults DEFAULT = new PipelineDefaults(100, 5, true, null);

        public PipelineDefaults {
            if (sedaSize <= 0) sedaSize = 100;
            if (concurrentConsumers <= 0) concurrentConsumers = 5;
        }
    }

    /**
     * Execution-tracker settings.
     *
     * @param enabled        whether to record recent executions (default: true)
     * @param maxPerPipeline bounded history per pipeline (default: 50)
     */
    public record TrackerProperties(boolean enabled, int maxPerPipeline) {
        public static final TrackerProperties DEFAULT =
                new TrackerProperties(true, PipelineExecutionTracker.DEFAULT_MAX_PER_PIPELINE);

        public TrackerProperties {
            if (maxPerPipeline <= 0) maxPerPipeline = PipelineExecutionTracker.DEFAULT_MAX_PER_PIPELINE;
        }
    }

    /**
     * HTTP-trigger endpoint settings.
     *
     * @param enabled  whether to expose POST /{basePath}/{id}/trigger (default: true)
     * @param basePath base URL path (default: /api/pipelines)
     */
    public record HttpTriggerProperties(boolean enabled, String basePath) {
        public static final HttpTriggerProperties DEFAULT =
                new HttpTriggerProperties(true, "/api/pipelines");

        public HttpTriggerProperties {
            if (basePath == null || basePath.isBlank()) basePath = "/api/pipelines";
        }
    }

    /**
     * Per-file pipeline location patterns. Each entry is a Spring
     * {@code ResourcePatternResolver} pattern, e.g.
     * {@code classpath*:jaiclaw/pipelines/*.yml} or
     * {@code file:/etc/jaiclaw/pipelines/*.yml}.
     *
     * <p>No default scan locations — users must explicitly set
     * {@code jaiclaw.pipeline.locations.patterns[]} to opt into per-file
     * pipelines. This avoids surprise loads from unrelated YAML files that
     * happen to live in conventional paths.
     */
    public record LocationProperties(List<String> patterns) {

        public static final LocationProperties EMPTY = new LocationProperties(List.of());

        public LocationProperties {
            if (patterns == null) {
                patterns = List.of();
            } else {
                patterns = List.copyOf(patterns);
            }
        }
    }

    public static final PipelineProperties DEFAULT =
            new PipelineProperties(false, null, null, null, null, null, null);

    public PipelineProperties {
        if (pipelines == null) pipelines = List.of();
        else pipelines = List.copyOf(pipelines);
        if (defaults == null) defaults = PipelineDefaults.DEFAULT;
        if (security == null) security = PipelineSecurityProperties.DEFAULT;
        if (tracker == null) tracker = TrackerProperties.DEFAULT;
        if (httpTrigger == null) httpTrigger = HttpTriggerProperties.DEFAULT;
        if (locations == null) locations = LocationProperties.EMPTY;
    }
}
