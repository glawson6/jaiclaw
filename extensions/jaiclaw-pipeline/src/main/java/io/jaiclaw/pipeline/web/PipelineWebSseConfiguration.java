package io.jaiclaw.pipeline.web;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.pipeline.PipelineProperties;
import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers the pipeline SSE broadcaster + controller when Spring WebMVC
 * ({@code SseEmitter}) is on the classpath and
 * {@code jaiclaw.pipeline.sse.enabled} is true.
 *
 * <p>Lives in its own auto-configuration sibling to
 * {@link PipelineWebConfiguration} so the surrounding
 * {@code PipelineAutoConfiguration} loads without spring-webmvc.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.pipeline.PipelineAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.SseEmitter")
@ConditionalOnProperty(
        prefix = "jaiclaw.pipeline.sse",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PipelineWebSseConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PipelineRegistry.class, PipelineExecutionTracker.class})
    public PipelineEventBroadcaster pipelineEventBroadcaster(
            PipelineRegistry registry,
            PipelineExecutionTracker tracker,
            ObjectProvider<TenantGuard> tenantGuardProvider,
            PipelineProperties properties) {
        // Resolve SseProperties from the parent PipelineProperties record —
        // Spring doesn't auto-expose nested @ConfigurationProperties records
        // as separate beans, so we pull the sub-record here instead of
        // asking Spring to inject it directly.
        PipelineProperties.SseProperties sse = properties.sse() != null
                ? properties.sse()
                : PipelineProperties.SseProperties.DEFAULT;
        return new PipelineEventBroadcaster(
                registry, tracker, tenantGuardProvider.getIfAvailable(), sse);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PipelineEventBroadcaster.class)
    public PipelineEventController pipelineEventController(
            PipelineEventBroadcaster broadcaster,
            PipelineRegistry registry,
            PipelineProperties properties) {
        return new PipelineEventController(broadcaster, registry, properties);
    }
}
