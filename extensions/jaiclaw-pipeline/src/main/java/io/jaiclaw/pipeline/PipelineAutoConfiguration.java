package io.jaiclaw.pipeline;

import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.audit.TrajectoryRecorder;
import io.jaiclaw.camel.GatewayServiceAccessor;
import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.pipeline.dsl.JaiClawPipeline;
import io.jaiclaw.pipeline.gateway.DefaultPipelineGateway;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import io.jaiclaw.pipeline.loader.PipelineFileLoader;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import io.jaiclaw.pipeline.validation.PipelineValidator;
import org.springframework.core.io.ResourceLoader;
import org.apache.camel.ProducerTemplate;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

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
            ResourceLoader resourceLoader,
            ObjectProvider<List<JaiClawPipeline>> codePipelinesProvider) {

        PipelineRegistry registry = new PipelineRegistry();

        if (!properties.enabled()) {
            log.debug("jaiclaw.pipeline.enabled=false — pipeline module disabled, registry is empty");
            return registry;
        }

        // Phase 1 — YAML-inline pipelines from jaiclaw.pipeline.pipelines[].
        for (PipelineDefinition definition : properties.pipelines()) {
            registry.register(definition);
            log.info("Registered YAML pipeline: '{}'", definition.id());
        }

        // Phase 1.5 — Per-file YAML pipelines from jaiclaw.pipeline.locations[].
        List<String> patterns = properties.locations().patterns();
        if (!patterns.isEmpty()) {
            PipelineFileLoader fileLoader = new PipelineFileLoader(resourceLoader);
            List<PipelineDefinition> filePipelines = fileLoader.loadAll(patterns);
            for (PipelineDefinition definition : filePipelines) {
                boolean override = registry.contains(definition.id());
                registry.register(definition);
                if (override) {
                    log.info("File pipeline '{}' overrides earlier definition", definition.id());
                } else {
                    log.info("Registered file pipeline: '{}'", definition.id());
                }
            }
        }

        // Phase 2 — Code-defined pipelines (code still wins on ID conflict).
        List<JaiClawPipeline> codePipelines = codePipelinesProvider.getIfAvailable();
        boolean codeProvided = codePipelines != null && !codePipelines.isEmpty();
        if (codeProvided) {
            for (JaiClawPipeline pipelineClass : codePipelines) {
                List<PipelineDefinition> definitions = pipelineClass.getDefinitions();
                for (PipelineDefinition definition : definitions) {
                    boolean override = registry.contains(definition.id());
                    registry.register(definition);
                    if (override) {
                        log.info("Code pipeline '{}' overrides earlier definition", definition.id());
                    } else {
                        log.info("Registered code pipeline: '{}'", definition.id());
                    }
                }
            }
        }

        // Fail-fast: enabled=true but no source configured.
        if (registry.size() == 0
                && properties.pipelines().isEmpty()
                && patterns.isEmpty()
                && !codeProvided) {
            throw new IllegalStateException(
                    "jaiclaw.pipeline.enabled=true but no pipeline source is configured. "
                            + "Provide at least one of: jaiclaw.pipeline.pipelines[] (inline), "
                            + "jaiclaw.pipeline.locations.patterns[] (per-file YAML), or a "
                            + "JaiClawPipeline @Configuration bean.");
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

    @Bean
    @ConditionalOnProperty(prefix = "jaiclaw.pipeline.tracker", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PipelineExecutionTracker pipelineExecutionTracker(PipelineProperties properties) {
        return new PipelineExecutionTracker(properties.tracker().maxPerPipeline());
    }

    @Bean
    @ConditionalOnBean(CamelContext.class)
    public PipelineGateway pipelineGateway(CamelContext camelContext, PipelineRegistry registry) {
        ProducerTemplate template = camelContext.createProducerTemplate();
        return new DefaultPipelineGateway(template, registry);
    }

    @Bean
    public PipelineValidator pipelineValidator(
            PipelineRegistry registry,
            PipelineProperties properties,
            ApplicationContext applicationContext,
            ObjectProvider<ChannelRegistry> channelRegistryProvider) {
        return new PipelineValidator(registry, properties, applicationContext, channelRegistryProvider);
    }

    /**
     * Run validation before the route initializer so misconfigured pipelines fail
     * startup with a single consolidated message instead of crashing mid-execution.
     *
     * <p>Only registered when the module is enabled — if disabled, the registry
     * is empty and there's nothing to validate.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(prefix = "jaiclaw.pipeline", name = "enabled", havingValue = "true")
    public ApplicationRunner pipelineValidationRunner(PipelineValidator validator) {
        return args -> {
            log.debug("Running pipeline validation");
            validator.validateOrThrow();
        };
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

    // Actuator endpoint + HTTP trigger controller live in their own auto-config
    // classes (PipelineActuatorConfiguration / PipelineWebConfiguration) so the
    // surrounding autoconfig still loads cleanly without actuator or spring-web.

    @Bean
    @ConditionalOnBean(CamelContext.class)
    @ConditionalOnProperty(prefix = "jaiclaw.pipeline", name = "enabled", havingValue = "true")
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
            PipelineTransportAuthenticator transportAuthenticator,
            ObjectProvider<PipelineExecutionTracker> trackerProvider) {

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
                        transportAuthenticator,
                        trackerProvider.getIfAvailable());

                camelContext.addRoutes(routeBuilder);
                count++;
            }

            if (count > 0) {
                log.info("Initialized {} pipeline route(s)", count);
            }
        };
    }
}
