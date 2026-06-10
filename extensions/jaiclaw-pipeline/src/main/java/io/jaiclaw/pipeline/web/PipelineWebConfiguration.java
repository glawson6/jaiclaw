package io.jaiclaw.pipeline.web;

import io.jaiclaw.pipeline.gateway.PipelineGateway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link PipelineTriggerController} when Spring Web is on the
 * classpath and {@code jaiclaw.pipeline.http-trigger.enabled} is true.
 * Lives in its own auto-configuration so the surrounding
 * {@code PipelineAutoConfiguration} loads without spring-web.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.pipeline.PipelineAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
@ConditionalOnProperty(
        prefix = "jaiclaw.pipeline.http-trigger",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PipelineWebConfiguration {

    @Bean
    @ConditionalOnBean(PipelineGateway.class)
    public PipelineTriggerController pipelineTriggerController(PipelineGateway gateway) {
        return new PipelineTriggerController(gateway);
    }
}
