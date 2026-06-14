package io.jaiclaw.agentmind.soul;

import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.agentmind.soul.hook.SoulPromptInjector;
import io.jaiclaw.agentmind.soul.store.AgentMindStoreProvider;
import io.jaiclaw.agentmind.soul.tool.SoulAgentTool;
import io.jaiclaw.agentmind.soul.mcp.SoulMcpToolProvider;
import io.jaiclaw.agentmind.soul.mcp.TenantSoulMcpToolProvider;
import io.jaiclaw.agentmind.soul.metrics.InstrumentedSoulProvider;
import io.jaiclaw.agentmind.soul.web.SoulDebugController;
import io.jaiclaw.agentmind.soul.web.TenantSoulController;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import io.jaiclaw.agentmind.soul.store.JsonAgentMindStoreProvider;
import io.jaiclaw.agentmind.soul.user.AgentMindUserKeyResolver;
import io.jaiclaw.agentmind.soul.user.IdentityLinkUserKeyResolver;
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
 * Pillar-level autoconfig for the agentmind Soul extension. Defaults OFF —
 * no beans are created until {@code jaiclaw.agentmind.soul.enabled=true}.
 *
 * <h2>Multi-tenancy conformance review (plan §5 task 1.10)</h2>
 *
 * Per analysis §5.8 checklist. Reviewed 2026-06-14 against the CLAUDE.md
 * conformance template:
 *
 * <table>
 *   <caption>Soul module conformance findings</caption>
 *   <tr><th>Check</th><th>Status</th><th>Evidence</th></tr>
 *   <tr>
 *     <td>Persistence isolation — path/PK/key includes tenantId</td>
 *     <td>PASS</td>
 *     <td>{@code FileSoulProvider#pathFor} dispatches on
 *         {@code TenantGuard#isMultiTenant()} to prefix
 *         {@code ${root}/{tenantId}/...}; SINGLE-mode collapses to
 *         {@code ${root}/...}. {@code FileSoulProviderSpec} covers both
 *         layouts plus cross-tenant read isolation.</td>
 *   </tr>
 *   <tr>
 *     <td>Per-user isolation</td>
 *     <td>N/A for Soul</td>
 *     <td>Soul keys on {@code (tenantId, agentId)} for AGENT scope and
 *         {@code (tenantId)} for TENANT scope. Per-user dimension lands
 *         with Memory + Tendencies in Phase 2 and Phase 3.</td>
 *   </tr>
 *   <tr>
 *     <td>Async hops wrapped via {@code TenantContextPropagator}</td>
 *     <td>N/A</td>
 *     <td>All writes are synchronous via the {@code soul} tool, REST
 *         controller, or MCP provider — the
 *         {@code SoulPromptInjector} hook runs synchronously inside the
 *         agent runtime's modifying-hook dispatch.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code TenantGuard} injection (not {@code TenantContextHolder}
 *         directly)</td>
 *     <td>PASS</td>
 *     <td>{@code FileSoulProvider}, {@code SoulPromptInjector},
 *         {@code SoulAgentTool}, {@code SoulDebugController}, and
 *         {@code TenantSoulController} all inject {@code TenantGuard} via
 *         their constructors. No direct {@code TenantContextHolder}
 *         access in the module.</td>
 *   </tr>
 *   <tr>
 *     <td>SINGLE-mode behaviour</td>
 *     <td>PASS</td>
 *     <td>{@code tenantId} resolves to {@code "default"} when
 *         {@code TenantGuard} is null or single-mode. File paths skip the
 *         tenant prefix subdirectory, matching the
 *         {@code JsonFileTaskStore} precedent.</td>
 *   </tr>
 *   <tr>
 *     <td>MCP tools forward {@code TenantContext}</td>
 *     <td>PASS</td>
 *     <td>Both {@code SoulMcpToolProvider#execute} and
 *         {@code TenantSoulMcpToolProvider#execute} extract
 *         {@code tenant.getTenantId()} on every call and pass it through
 *         to the store layer. No fallback to ambient
 *         {@code TenantContextHolder}.</td>
 *   </tr>
 *   <tr>
 *     <td>Tenant-scope opt-in independent of pillar opt-in</td>
 *     <td>PASS</td>
 *     <td>{@code TenantSoulRestConfig} is a nested {@code @Configuration}
 *         gated by {@code jaiclaw.agentmind.soul.tenant.enabled=true} on top
 *         of the pillar gate; {@code SoulAutoConfigDisabledSpec} verifies
 *         that enabling the pillar alone leaves
 *         {@code tenant.enabled=false} and creates no tenant beans.</td>
 *   </tr>
 * </table>
 *
 * <p>Tenant-scope sub-config gated separately by
 * {@code jaiclaw.agentmind.soul.tenant.enabled=true}. Plan §5 task 1.2 —
 * autoconfig scaffold; task 1.10 — this conformance block.
 */
@AutoConfiguration
@Configuration
@EnableConfigurationProperties(AgentMindSoulProperties.class)
@ConditionalOnProperty(prefix = "jaiclaw.agentmind.soul", name = "enabled", havingValue = "true")
public class AgentMindSoulAutoConfiguration {

    @Bean
    public String agentmindSoulModuleMarker() {
        // Sentinel bean. Specs assert its presence/absence to verify the
        // @ConditionalOnProperty gate. Replaced by concrete beans
        // (SoulPromptInjector, agent tool, REST controllers, MCP providers,
        // actuator counters) as later tasks land.
        return "agentmind-soul-enabled";
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentMindUserKeyResolver agentmindUserKeyResolver(ObjectProvider<IdentityResolver> identityResolver) {
        return new IdentityLinkUserKeyResolver(identityResolver.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentMindStoreProvider agentmindStoreProvider(AgentMindSoulProperties props,
                                                   ObjectProvider<TenantGuard> tenantGuard) {
        return new JsonAgentMindStoreProvider(Path.of(props.rootDir()), tenantGuard.getIfAvailable());
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
    public SoulProvider soulProvider(AgentMindStoreProvider storeProvider,
                                     ObjectProvider<MeterRegistry> meterRegistry) {
        SoulProvider bare = storeProvider.soulStore();
        MeterRegistry registry = meterRegistry.getIfAvailable();
        return registry != null ? new InstrumentedSoulProvider(bare, registry) : bare;
    }

    @Bean
    @ConditionalOnMissingBean
    public SoulPromptInjector soulPromptInjector(SoulProvider soulProvider,
                                                 ObjectProvider<TenantGuard> tenantGuard,
                                                 AgentMindSoulProperties props) {
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
     * Debug read endpoint at {@code GET /api/agentmind/soul/agent/{agentId}}.
     * Off by default; flip {@code jaiclaw.agentmind.soul.rest.enabled=true}
     * to enable. Intended for ops only.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "jaiclaw.agentmind.soul.rest", name = "enabled", havingValue = "true")
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
     * {@code jaiclaw.agentmind.soul.tenant.enabled=true} to enable. Role-guarded
     * by {@code jaiclaw.agentmind.soul.tenant.write.roles}.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "jaiclaw.agentmind.soul.tenant", name = "enabled", havingValue = "true")
    public static class TenantSoulRestConfig {
        @Bean
        @ConditionalOnMissingBean
        public TenantSoulController tenantSoulController(SoulProvider soulProvider,
                                                         ObjectProvider<TenantGuard> tenantGuard,
                                                         AgentMindSoulProperties props) {
            return new TenantSoulController(soulProvider, tenantGuard.getIfAvailable(), props);
        }

        @Bean
        @ConditionalOnMissingBean
        public TenantSoulMcpToolProvider tenantSoulMcpToolProvider(SoulProvider soulProvider) {
            return new TenantSoulMcpToolProvider(soulProvider);
        }
    }
}
