package io.jaiclaw.pipeline.authoring;

import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.pipeline.AgentStageProcessor;
import io.jaiclaw.pipeline.BeanStageProcessor;
import io.jaiclaw.pipeline.CamelStageProcessor;
import io.jaiclaw.pipeline.PipelineAuditor;
import io.jaiclaw.pipeline.PipelineHookFirer;
import io.jaiclaw.pipeline.PipelineMetrics;
import io.jaiclaw.pipeline.PipelineProperties;
import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.pipeline.PipelineSecurityGuard;
import io.jaiclaw.pipeline.PipelineTransportAuthenticator;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import io.jaiclaw.pipeline.gateway.PipelineSyncCoordinator;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import io.jaiclaw.pipeline.validation.PipelineValidator;
import io.jaiclaw.plugin.HookRunner;
import org.apache.camel.CamelContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Auto-configuration for the Pipeline Studio authoring plane
 * (Phase 1 + Phase 3 of the Pipeline Studio buildout).
 *
 * <p>Activates when:
 * <ul>
 *   <li>{@link DispatcherServlet} is on the classpath (WebMVC required).</li>
 *   <li>{@code jaiclaw.pipeline.authoring.enabled=true}. Opt-in — not
 *       enabled by default even when the module is on the classpath,
 *       because the endpoints today are unauthenticated (role-based
 *       authz lands in Phase 3).</li>
 * </ul>
 *
 * <p>Phase 3 wiring — {@link PipelineLifecycleManager} +
 * {@link PipelineTestRunService} + {@link PipelineDeploymentController}
 * — activates only when {@link CamelContext} and {@link PipelineGateway}
 * are on the classpath (they always are when {@code jaiclaw-pipeline}
 * is active with a Camel starter).
 *
 * <p>Every bean is {@code @ConditionalOnMissingBean} so adopters can
 * substitute their own store / catalog / controller / lifecycle-manager
 * wholesale.
 */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "jaiclaw.pipeline.authoring", name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(PipelineAuthoringProperties.class)
public class JaiClawPipelineAuthoringAutoConfiguration {

    // ── Phase 1 beans ────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(PipelineDraftStore.class)
    public PipelineDraftStore pipelineDraftStore(PipelineAuthoringProperties props,
                                                  ObjectProvider<TenantGuard> tenantGuardProvider) {
        TenantGuard tenantGuard = tenantGuardProvider.getIfAvailable(
                () -> new TenantGuard(TenantProperties.DEFAULT));
        return new JsonFilePipelineDraftStore(props.storagePath(), tenantGuard);
    }

    @Bean
    @ConditionalOnMissingBean(PipelineCatalogService.class)
    public PipelineCatalogService pipelineCatalogService(ApplicationContext applicationContext,
                                                          ObjectProvider<ChannelRegistry> channelRegistryProvider) {
        return new PipelineCatalogService(applicationContext, channelRegistryProvider);
    }

    @Bean
    @ConditionalOnMissingBean(PipelineStudioController.class)
    public PipelineStudioController pipelineStudioController(PipelineDraftStore store,
                                                              PipelineCatalogService catalog,
                                                              PipelineValidator validator) {
        return new PipelineStudioController(store, catalog, validator);
    }

    // ── Phase 3 beans (hot deploy + test-run + authz + URI allowlist) ──

    @Bean
    @ConditionalOnMissingBean(UriSchemeAllowlist.class)
    public UriSchemeAllowlist uriSchemeAllowlist(PipelineAuthoringProperties props) {
        return new UriSchemeAllowlist(props.security().allowedUriSchemes());
    }

    @Bean
    @ConditionalOnMissingBean(name = "pipelineAuthzExpressions")
    public PipelineAuthzExpressions pipelineAuthzExpressions(PipelineAuthoringProperties props) {
        return new PipelineAuthzExpressions(props.roles());
    }

    @Bean
    @ConditionalOnBean(CamelContext.class)
    @ConditionalOnMissingBean(PipelineLifecycleManager.class)
    public PipelineLifecycleManager pipelineLifecycleManager(
            PipelineRegistry registry,
            PipelineValidator validator,
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
            ObjectProvider<PipelineExecutionTracker> trackerProvider,
            ObjectProvider<PipelineSyncCoordinator> syncCoordinatorProvider,
            ObjectProvider<HookRunner> hookRunnerProvider,
            ObjectProvider<ApplicationEventPublisher> publisherProvider,
            PipelineAuthoringProperties authoringProperties) {
        return new PipelineLifecycleManager(
                registry,
                validator,
                properties,
                camelContext,
                agentProcessorProvider.getIfAvailable(),
                beanProcessor,
                camelProcessorProvider.getIfAvailable(),
                auditor,
                hookFirer,
                metricsProvider.getIfAvailable(),
                securityGuard,
                transportAuthenticator,
                trackerProvider.getIfAvailable(),
                syncCoordinatorProvider.getIfAvailable(),
                hookRunnerProvider.getIfAvailable(),
                publisherProvider.getIfAvailable(),
                authoringProperties.hotReload().drainTimeout());
    }

    @Bean
    @ConditionalOnBean({CamelContext.class, PipelineGateway.class})
    @ConditionalOnMissingBean(PipelineTestRunService.class)
    public PipelineTestRunService pipelineTestRunService(PipelineLifecycleManager lifecycle,
                                                          PipelineGateway gateway) {
        return new PipelineTestRunService(lifecycle, gateway, null);
    }

    @Bean
    @ConditionalOnBean({PipelineLifecycleManager.class, PipelineTestRunService.class})
    @ConditionalOnMissingBean(PipelineDeploymentController.class)
    public PipelineDeploymentController pipelineDeploymentController(
            PipelineDraftStore store,
            PipelineLifecycleManager lifecycle,
            PipelineTestRunService testRun,
            PipelineValidator validator,
            UriSchemeAllowlist uriAllowlist) {
        return new PipelineDeploymentController(store, lifecycle, testRun, validator, uriAllowlist);
    }

    @Bean
    @ConditionalOnBean(PipelineLifecycleManager.class)
    @ConditionalOnMissingBean(PipelineAuthoringMcpToolProvider.class)
    public PipelineAuthoringMcpToolProvider pipelineAuthoringMcpToolProvider(
            PipelineValidator validator,
            PipelineDraftStore draftStore,
            PipelineLifecycleManager lifecycle,
            PipelineAuthoringProperties props) {
        return new PipelineAuthoringMcpToolProvider(
                validator, draftStore, lifecycle, props.mcp().deployEnabled());
    }
}
