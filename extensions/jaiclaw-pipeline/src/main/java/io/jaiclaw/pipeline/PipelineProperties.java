package io.jaiclaw.pipeline;

import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
 * @param sync        synchronous-result coordinator settings
 */
@ConfigurationProperties(prefix = "jaiclaw.pipeline")
public record PipelineProperties(
        boolean enabled,
        List<PipelineDefinition> pipelines,
        PipelineDefaults defaults,
        PipelineSecurityProperties security,
        TrackerProperties tracker,
        HttpTriggerProperties httpTrigger,
        LocationProperties locations,
        SyncProperties sync
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
     * <p>The HTTP trigger surface is alias-routed: callers POST a trigger
     * resource containing a logical pipeline name (e.g. {@code "ticket-scoring"}),
     * and the framework resolves it to an internal pipeline id via the
     * {@link #allowed allowed alias map}. Aliases not in the map produce 404.
     * This keeps internal pipeline ids out of the API surface and turns path
     * tampering into a no-op.
     *
     * @param enabled  whether to expose {@code POST {basePath}/trigger} (default: true)
     * @param basePath base URL path (default: {@code /api/pipelines})
     * @param allowed  alias → internal-pipeline-id map. The framework only
     *                 honors HTTP trigger requests whose {@code pipeline}
     *                 field matches a key here. Default: {@link Map#of() empty}
     *                 (no pipelines callable via HTTP — safe-by-default).
     */
    public record HttpTriggerProperties(
            boolean enabled,
            String basePath,
            Map<String, String> allowed
    ) {
        public static final HttpTriggerProperties DEFAULT =
                new HttpTriggerProperties(true, "/api/pipelines", Map.of());

        public HttpTriggerProperties {
            if (basePath == null || basePath.isBlank()) basePath = "/api/pipelines";
            // Note: we intentionally do NOT defensively copy `allowed` here.
            // Spring Boot's @ConfigurationProperties record-binding builds the
            // map incrementally as properties arrive; wrapping it in an
            // unmodifiable view inside the compact constructor breaks that
            // builder path. The map is reachable only through the record
            // accessor, so leaving it as Spring provides it is safe in
            // practice. We still validate keys/values, but the validation
            // tolerates a null map and skips entries with null keys/values
            // (binding never produces those — defense-in-depth only).
            if (allowed == null) {
                allowed = Map.of();
            } else {
                for (Map.Entry<String, String> entry : allowed.entrySet()) {
                    String alias = entry.getKey();
                    String pipelineId = entry.getValue();
                    if (alias == null || alias.isBlank()) {
                        throw new IllegalArgumentException(
                                "jaiclaw.pipeline.http-trigger.allowed: alias must not be blank");
                    }
                    if (pipelineId == null || pipelineId.isBlank()) {
                        throw new IllegalArgumentException(
                                "jaiclaw.pipeline.http-trigger.allowed['" + alias
                                        + "']: pipeline id must not be blank");
                    }
                }
            }
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

    /**
     * Synchronous-result coordinator settings. Controls the
     * {@code PipelineSyncCoordinator} that backs
     * {@code PipelineGateway#triggerAsync(...)} /
     * {@code PipelineGateway#triggerAndAwait(...)}.
     *
     * @param maxPending          maximum number of in-flight synchronous executions
     *                            tracked at once. New {@code triggerAsync} calls beyond
     *                            this cap return an already-failed future. Default: 1024.
     * @param orphanTtl           duration after which a pending future is reaped by the
     *                            sweep with {@code PipelineOrphanException}. Bounds the
     *                            leak when the route never completes (JVM crash,
     *                            route-builder bug). Default: 10 minutes.
     * @param sweepInterval       how often the sweep runs. Default: 60 seconds.
     * @param completionPoolSize  worker threads used by the coordinator to publish
     *                            completion results to waiters. Decouples user
     *                            {@code .thenApply} continuations from the Camel
     *                            thread. Default: 2.
     */
    public record SyncProperties(
            int maxPending,
            Duration orphanTtl,
            Duration sweepInterval,
            int completionPoolSize
    ) {
        public static final SyncProperties DEFAULT = new SyncProperties(
                1024,
                Duration.ofMinutes(10),
                Duration.ofSeconds(60),
                2
        );

        public SyncProperties {
            if (maxPending <= 0) maxPending = 1024;
            if (orphanTtl == null || orphanTtl.isZero() || orphanTtl.isNegative()) {
                orphanTtl = Duration.ofMinutes(10);
            }
            if (sweepInterval == null || sweepInterval.isZero() || sweepInterval.isNegative()) {
                sweepInterval = Duration.ofSeconds(60);
            }
            if (completionPoolSize <= 0) completionPoolSize = 2;
        }
    }

    public static final PipelineProperties DEFAULT =
            new PipelineProperties(false, null, null, null, null, null, null, null);

    public PipelineProperties {
        if (pipelines == null) pipelines = List.of();
        else pipelines = List.copyOf(pipelines);
        if (defaults == null) defaults = PipelineDefaults.DEFAULT;
        if (security == null) security = PipelineSecurityProperties.DEFAULT;
        if (tracker == null) tracker = TrackerProperties.DEFAULT;
        if (httpTrigger == null) httpTrigger = HttpTriggerProperties.DEFAULT;
        if (locations == null) locations = LocationProperties.EMPTY;
        if (sync == null) sync = SyncProperties.DEFAULT;
    }
}
