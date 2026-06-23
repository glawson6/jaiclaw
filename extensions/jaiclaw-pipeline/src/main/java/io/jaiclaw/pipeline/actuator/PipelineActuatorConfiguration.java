package io.jaiclaw.pipeline.actuator;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.springframework.beans.factory.ObjectProvider;
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

    /**
     * Wires {@link TenantGuard} when available (SEV-010 multi-tenant
     * visibility filter). Uses {@link ObjectProvider} so the endpoint
     * still loads in apps that don't bring in jaiclaw-spring-boot-starter
     * (e.g. test fixtures wiring the pipeline module standalone).
     */
    @Bean
    @ConditionalOnBean({PipelineRegistry.class, PipelineExecutionTracker.class})
    public PipelineActuatorEndpoint pipelineActuatorEndpoint(
            PipelineRegistry registry,
            PipelineExecutionTracker tracker,
            ObjectProvider<TenantGuard> tenantGuard) {
        return new PipelineActuatorEndpoint(registry, tracker, tenantGuard.getIfAvailable());
    }
}
