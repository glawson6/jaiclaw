package io.jaiclaw.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for JaiClaw observability.
 * Creates {@link JaiClawMetrics} when a {@link MeterRegistry} is available,
 * and makes the {@link ObservationRegistry} available for observation-based instrumentation.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class ObservabilityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    public JaiClawMetrics jaiClawMetrics(MeterRegistry meterRegistry) {
        log.info("JaiClaw observability initialized — custom metrics enabled");
        return new JaiClawMetrics(meterRegistry);
    }
}
