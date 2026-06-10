package io.jaiclaw.pipeline.actuator;

import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link PipelineActuatorEndpoint} when Spring Boot Actuator is on
 * the classpath. Lives in its own auto-configuration so the surrounding
 * {@code PipelineAutoConfiguration} loads without actuator.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.pipeline.PipelineAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
public class PipelineActuatorConfiguration {

    @Bean
    @ConditionalOnBean({PipelineRegistry.class, PipelineExecutionTracker.class})
    public PipelineActuatorEndpoint pipelineActuatorEndpoint(
            PipelineRegistry registry, PipelineExecutionTracker tracker) {
        return new PipelineActuatorEndpoint(registry, tracker);
    }
}
