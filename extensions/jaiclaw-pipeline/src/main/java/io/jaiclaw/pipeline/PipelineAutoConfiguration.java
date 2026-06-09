package io.jaiclaw.pipeline;

import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.audit.TrajectoryRecorder;
import io.jaiclaw.camel.GatewayServiceAccessor;
import io.jaiclaw.pipeline.dsl.JaiClawPipeline;
import io.jaiclaw.plugin.HookRunner;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Auto-configuration for the pipeline module.
 *
 * <p>Two-phase pipeline discovery:
 * <ol>
 *   <li>YAML — from {@code jaiclaw.pipeline.pipelines[]} configuration properties</li>
 *   <li>Code — from {@link JaiClawPipeline} beans in the Spring context (code wins on ID conflict)</li>
 * </ol>
 */
@AutoConfiguration
@AutoConfigureAfter(name = {
        "io.jaiclaw.camel.JaiClawCamelAutoConfiguration",
        "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration"
})
@ConditionalOnClass(name = "org.apache.camel.CamelContext")
@EnableConfigurationProperties(PipelineProperties.class)
public class PipelineAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PipelineAutoConfiguration.class);

    @Bean
    public PipelineRegistry pipelineRegistry(
            PipelineProperties properties,
            ObjectProvider<List<JaiClawPipeline>> codePipelinesProvider) {

        PipelineRegistry registry = new PipelineRegistry();

        // Phase 1: Register YAML-defined pipelines
        for (PipelineDefinition definition : properties.pipelines()) {
            registry.register(definition);
            log.info("Registered YAML pipeline: '{}'", definition.id());
        }

        // Phase 2: Discover code-defined pipelines (code wins on ID conflict)
        List<JaiClawPipeline> codePipelines = codePipelinesProvider.getIfAvailable();
        if (codePipelines != null) {
            for (JaiClawPipeline pipelineClass : codePipelines) {
                List<PipelineDefinition> definitions = pipelineClass.getDefinitions();
                for (PipelineDefinition definition : definitions) {
                    boolean override = registry.contains(definition.id());
                    registry.register(definition);
                    if (override) {
                        log.info("Code pipeline '{}' overrides YAML definition", definition.id());
                    } else {
                        log.info("Registered code pipeline: '{}'", definition.id());
                    }
                }
            }
        }

        log.info("Pipeline registry: {} pipeline(s) registered", registry.size());
        return registry;
    }

    @Bean
    @ConditionalOnBean(GatewayServiceAccessor.class)
    public AgentStageProcessor agentStageProcessor(GatewayServiceAccessor gateway) {
        return new AgentStageProcessor(gateway);
    }

    @Bean
    public BeanStageProcessor beanStageProcessor(ApplicationContext applicationContext) {
        return new BeanStageProcessor(applicationContext);
    }

    @Bean
    @ConditionalOnBean(CamelContext.class)
    public CamelStageProcessor camelStageProcessor(CamelContext camelContext) {
        return new CamelStageProcessor(camelContext.createProducerTemplate());
    }

    @Bean
    public PipelineAuditor pipelineAuditor(
            ObjectProvider<AuditLogger> auditLoggerProvider,
            ObjectProvider<TrajectoryRecorder> trajectoryRecorderProvider) {
        return new PipelineAuditor(
                auditLoggerProvider.getIfAvailable(),
                trajectoryRecorderProvider.getIfAvailable());
    }

    @Bean
    public PipelineHookFirer pipelineHookFirer(ObjectProvider<HookRunner> hookRunnerProvider) {
        return new PipelineHookFirer(hookRunnerProvider.getIfAvailable());
    }

    @Bean
    public PipelineSecurityGuard pipelineSecurityGuard(
            PipelineProperties properties,
            ObjectProvider<AuditLogger> auditLoggerProvider) {
        return new PipelineSecurityGuard(
                properties.security(),
                auditLoggerProvider.getIfAvailable());
    }

    @Bean
    public PipelineTransportAuthenticator pipelineTransportAuthenticator() {
        return new PipelineTransportAuthenticator();
    }

    @Configuration
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class PipelineMetricsConfiguration {
        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        public PipelineMetrics pipelineMetrics(MeterRegistry registry) {
            return new PipelineMetrics(registry);
        }
    }

    @Bean
    @ConditionalOnBean(CamelContext.class)
    public ApplicationRunner pipelineRouteInitializer(
            PipelineRegistry registry,
            PipelineProperties properties,
            CamelContext camelContext,
            ObjectProvider<AgentStageProcessor> agentProcessorProvider,
            BeanStageProcessor beanProcessor,
            ObjectProvider<CamelStageProcessor> camelProcessorProvider,
            PipelineAuditor auditor,
            PipelineHookFirer hookFirer,
            ObjectProvider<PipelineMetrics> metricsProvider,
            PipelineSecurityGuard securityGuard,
            PipelineTransportAuthenticator transportAuthenticator) {

        return args -> {
            int count = 0;
            for (PipelineDefinition definition : registry.getAll()) {
                if (!definition.enabled()) {
                    log.info("Pipeline '{}' is disabled, skipping", definition.id());
                    continue;
                }

                PipelineRouteBuilder routeBuilder = new PipelineRouteBuilder(
                        definition,
                        properties.defaults(),
                        agentProcessorProvider.getIfAvailable(),
                        beanProcessor,
                        camelProcessorProvider.getIfAvailable(),
                        auditor,
                        hookFirer,
                        metricsProvider.getIfAvailable(),
                        securityGuard,
                        transportAuthenticator);

                camelContext.addRoutes(routeBuilder);
                count++;
            }

            if (count > 0) {
                log.info("Initialized {} pipeline route(s)", count);
            }
        };
    }
}
