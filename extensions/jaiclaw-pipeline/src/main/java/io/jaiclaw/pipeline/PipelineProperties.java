package io.jaiclaw.pipeline;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for the pipeline module.
 *
 * @param pipelines YAML-defined pipeline definitions
 * @param defaults  global defaults for inter-stage transport
 * @param security  global security settings (per-pipeline overrides take precedence)
 */
@ConfigurationProperties(prefix = "jaiclaw.pipeline")
public record PipelineProperties(
        List<PipelineDefinition> pipelines,
        PipelineDefaults defaults,
        PipelineSecurityProperties security
) {
    /**
     * Default SEDA queue configuration for inter-stage transport.
     *
     * @param sedaSize            SEDA queue buffer size (default: 100)
     * @param concurrentConsumers number of concurrent consumers per queue (default: 5)
     * @param blockWhenFull       whether to block producers when the queue is full (default: true)
     */
    public record PipelineDefaults(
            int sedaSize,
            int concurrentConsumers,
            boolean blockWhenFull
    ) {
        public static final PipelineDefaults DEFAULT = new PipelineDefaults(100, 5, true);

        public PipelineDefaults {
            if (sedaSize <= 0) sedaSize = 100;
            if (concurrentConsumers <= 0) concurrentConsumers = 5;
        }
    }

    public static final PipelineProperties DEFAULT =
            new PipelineProperties(null, null, null);

    public PipelineProperties {
        if (pipelines == null) pipelines = List.of();
        else pipelines = List.copyOf(pipelines);
        if (defaults == null) defaults = PipelineDefaults.DEFAULT;
        if (security == null) security = PipelineSecurityProperties.DEFAULT;
    }
}
