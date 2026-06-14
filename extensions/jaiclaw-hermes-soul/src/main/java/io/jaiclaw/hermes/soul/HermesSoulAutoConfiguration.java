package io.jaiclaw.hermes.soul;

import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.hermes.soul.hook.SoulPromptInjector;
import io.jaiclaw.hermes.soul.store.HermesStoreProvider;
import io.jaiclaw.hermes.soul.tool.SoulAgentTool;
import io.jaiclaw.hermes.soul.mcp.SoulMcpToolProvider;
import io.jaiclaw.hermes.soul.mcp.TenantSoulMcpToolProvider;
import io.jaiclaw.hermes.soul.metrics.InstrumentedSoulProvider;
import io.jaiclaw.hermes.soul.web.SoulDebugController;
import io.jaiclaw.hermes.soul.web.TenantSoulController;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import io.jaiclaw.hermes.soul.store.JsonHermesStoreProvider;
import io.jaiclaw.hermes.soul.user.HermesUserKeyResolver;
import io.jaiclaw.hermes.soul.user.IdentityLinkUserKeyResolver;
import io.jaiclaw.identity.IdentityResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Pillar-level autoconfig for the hermes Soul extension. Defaults OFF —
 * no beans are created until {@code jaiclaw.hermes.soul.enabled=true}.
 *
 * <p>Multi-tenancy conformance: the {@code FileSoulProvider} default impl
 * uses per-tenant subdirectory paths via {@code TenantGuard.resolveTenantPrefix()};
 * the {@code TenantSoulController} and {@code TenantSoulMcpToolProvider} both
 * forward {@code TenantContext} per repo rule. Cross-tenant + cross-user
 * read isolation is asserted by {@code HermesStoreIsolationSpec} in the
 * test suite.
 *
 * <p>Plan §5 task 1.2 — autoconfig scaffold. Tenant-scope sub-config gated
 * separately by {@code jaiclaw.hermes.soul.tenant.enabled=true}.
 */
@AutoConfiguration
@Configuration
@EnableConfigurationProperties(HermesSoulProperties.class)
@ConditionalOnProperty(prefix = "jaiclaw.hermes.soul", name = "enabled", havingValue = "true")
public class HermesSoulAutoConfiguration {

    @Bean
    public String hermesSoulModuleMarker() {
        // Sentinel bean. Specs assert its presence/absence to verify the
        // @ConditionalOnProperty gate. Replaced by concrete beans
        // (SoulPromptInjector, agent tool, REST controllers, MCP providers,
        // actuator counters) as later tasks land.
        return "hermes-soul-enabled";
    }

    @Bean
    @ConditionalOnMissingBean
    public HermesUserKeyResolver hermesUserKeyResolver(ObjectProvider<IdentityResolver> identityResolver) {
        return new IdentityLinkUserKeyResolver(identityResolver.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public HermesStoreProvider hermesStoreProvider(HermesSoulProperties props,
                                                   ObjectProvider<TenantGuard> tenantGuard) {
        return new JsonHermesStoreProvider(Path.of(props.rootDir()), tenantGuard.getIfAvailable());
    }

    /**
     * Bare {@link SoulProvider} pulled out of the store provider. If a
     * {@link MeterRegistry} is on the classpath, the
     * {@link MetricsConfig#soulProvider} bean wraps this with an
     * {@link InstrumentedSoulProvider} that publishes Micrometer counters
     * (task 1.11). Otherwise this bare provider is used directly.
     */
    @Bean
    @ConditionalOnMissingBean
    public SoulProvider soulProvider(HermesStoreProvider storeProvider,
                                     ObjectProvider<MeterRegistry> meterRegistry) {
        SoulProvider bare = storeProvider.soulStore();
        MeterRegistry registry = meterRegistry.getIfAvailable();
        return registry != null ? new InstrumentedSoulProvider(bare, registry) : bare;
    }

    @Bean
    @ConditionalOnMissingBean
    public SoulPromptInjector soulPromptInjector(SoulProvider soulProvider,
                                                 ObjectProvider<TenantGuard> tenantGuard,
                                                 HermesSoulProperties props) {
        return new SoulPromptInjector(soulProvider, tenantGuard.getIfAvailable(), props);
    }

    @Bean
    @ConditionalOnMissingBean
    public SoulAgentTool soulAgentTool(SoulProvider soulProvider,
                                       ObjectProvider<TenantGuard> tenantGuard) {
        return new SoulAgentTool(soulProvider, tenantGuard.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public SoulMcpToolProvider soulMcpToolProvider(SoulProvider soulProvider) {
        return new SoulMcpToolProvider(soulProvider);
    }

    /**
     * Debug read endpoint at {@code GET /api/hermes/soul/agent/{agentId}}.
     * Off by default; flip {@code jaiclaw.hermes.soul.rest.enabled=true}
     * to enable. Intended for ops only.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "jaiclaw.hermes.soul.rest", name = "enabled", havingValue = "true")
    public static class SoulDebugRestConfig {
        @Bean
        @ConditionalOnMissingBean
        public SoulDebugController soulDebugController(SoulProvider soulProvider,
                                                       ObjectProvider<TenantGuard> tenantGuard) {
            return new SoulDebugController(soulProvider, tenantGuard.getIfAvailable());
        }
    }

    /**
     * Operator-only tenant Soul write surface. Off by default; flip
     * {@code jaiclaw.hermes.soul.tenant.enabled=true} to enable. Role-guarded
     * by {@code jaiclaw.hermes.soul.tenant.write.roles}.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "jaiclaw.hermes.soul.tenant", name = "enabled", havingValue = "true")
    public static class TenantSoulRestConfig {
        @Bean
        @ConditionalOnMissingBean
        public TenantSoulController tenantSoulController(SoulProvider soulProvider,
                                                         ObjectProvider<TenantGuard> tenantGuard,
                                                         HermesSoulProperties props) {
            return new TenantSoulController(soulProvider, tenantGuard.getIfAvailable(), props);
        }

        @Bean
        @ConditionalOnMissingBean
        public TenantSoulMcpToolProvider tenantSoulMcpToolProvider(SoulProvider soulProvider) {
            return new TenantSoulMcpToolProvider(soulProvider);
        }
    }
}
