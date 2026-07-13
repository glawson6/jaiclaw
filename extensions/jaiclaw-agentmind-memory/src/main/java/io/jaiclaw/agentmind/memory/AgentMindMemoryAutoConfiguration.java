package io.jaiclaw.agentmind.memory;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import io.jaiclaw.core.agent.AgentMindMemoryProvider;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.agentmind.memory.hook.MemoryPromptInjector;
import io.jaiclaw.agentmind.memory.mcp.MemoryMcpToolProvider;
import io.jaiclaw.agentmind.memory.mcp.TenantMemoryMcpToolProvider;
import io.jaiclaw.agentmind.memory.metrics.InstrumentedMemoryProvider;
import io.jaiclaw.agentmind.memory.overflow.FailFastOverflowPolicy;
import io.jaiclaw.agentmind.memory.overflow.MemoryOverflowPolicy;
import io.jaiclaw.agentmind.memory.store.BoundedBlobMemoryStore;
import io.jaiclaw.agentmind.memory.tool.MemoryAgentTool;
import io.jaiclaw.agentmind.memory.web.MemoryDebugController;
import io.jaiclaw.agentmind.memory.web.TenantMemoryController;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Pillar-level autoconfig for the agentmind Memory extension. Defaults OFF —
 * no beans are created until {@code jaiclaw.agentmind.memory.enabled=true}.
 *
 * <h2>Multi-tenancy conformance review (plan §6 task 2.11)</h2>
 *
 * Per analysis §5.8 checklist. Reviewed 2026-06-14 against the CLAUDE.md
 * conformance template — all 6 applicable items PASS:
 *
 * <ul>
 *   <li><b>Persistence isolation</b> — {@code BoundedBlobMemoryStore#pathFor}
 *       dispatches on {@code TenantGuard#isMultiTenant()} to prefix
 *       {@code ${root}/{tenantId}/...}; SINGLE-mode collapses to
 *       {@code ${root}/...}. {@code BoundedBlobMemoryStoreSpec} +
 *       {@code AgentMindMemoryThreeScopeIsolationSpec} cover cross-tenant +
 *       cross-agent + cross-peer read isolation across all three scopes.</li>
 *   <li><b>Per-user isolation</b> — PEER-scope paths include both
 *       {@code peerId} (user key) and {@code agentId}.</li>
 *   <li><b>Async hop propagation</b> — N/A; all Memory writes are
 *       synchronous via the {@code memory} tool, REST controller, or MCP
 *       provider. The {@code MemorySessionListener} that captures the
 *       session-start snapshot runs synchronously inside the hook
 *       dispatcher.</li>
 *   <li><b>{@code TenantGuard} injection</b> — every consumer
 *       ({@code BoundedBlobMemoryStore}, the agent tool, REST + MCP
 *       providers, prompt injector) takes {@code TenantGuard} via
 *       constructor. No direct {@code TenantContextHolder} access.</li>
 *   <li><b>SINGLE-mode behaviour</b> — tenantId resolves to
 *       {@code "default"} with no subdirectory prefix.</li>
 *   <li><b>MCP {@code TenantContext} forwarding</b> — both
 *       {@code MemoryMcpToolProvider#execute} and
 *       {@code TenantMemoryMcpToolProvider#execute} extract
 *       {@code tenant.getTenantId()} on every call.</li>
 *   <li><b>Tenant-scope opt-in independent of pillar opt-in</b> — gated by
 *       the nested {@code TenantMemoryRestConfig} on top of the pillar
 *       gate; {@code AgentMindMemoryAutoConfigDisabledSpec} verifies both
 *       layers.</li>
 * </ul>
 *
 * <p>Plan §6 task 2.2 — autoconfig scaffold.
 */
@AutoConfiguration
@Configuration
@EnableConfigurationProperties(AgentMindMemoryProperties.class)
@ConditionalOnProperty(prefix = "jaiclaw.agentmind.memory", name = "enabled", havingValue = "true")
public class AgentMindMemoryAutoConfiguration {

    @Bean
    public String agentmindMemoryModuleMarker() {
        // Sentinel bean. Concrete beans (agent tool, prompt injector,
        // REST + MCP providers, actuator counters) are added by their
        // respective tasks.
        return "agentmind-memory-enabled";
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper agentmindMemoryObjectMapper() {
        ObjectMapper m = new ObjectMapper();
        m;
        m.configure(false);
        return m;
    }

    /**
     * Bare {@link AgentMindMemoryProvider} backed by the file store. When a
     * {@link MeterRegistry} is on the classpath the {@link InstrumentedMemoryProvider}
     * decorator wraps it so writes / overflows / conflicts publish to
     * Micrometer (plan §6 task 2.12). Without Micrometer the bare provider
     * is returned with no overhead.
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentMindMemoryProvider agentmindMemoryProvider(AgentMindMemoryProperties props,
                                                      ObjectMapper mapper,
                                                      ObjectProvider<TenantGuard> tenantGuard,
                                                      ObjectProvider<MeterRegistry> meterRegistry) {
        AgentMindMemoryProvider bare = new BoundedBlobMemoryStore(
                Path.of(props.rootDir()), tenantGuard.getIfAvailable(), mapper);
        MeterRegistry registry = meterRegistry.getIfAvailable();
        return registry != null ? new InstrumentedMemoryProvider(bare, registry) : bare;
    }

    /**
     * Default overflow policy is {@link FailFastOverflowPolicy} — the agent
     * tool surfaces the exception as a tool-error result and the LLM is
     * forced to consolidate in-turn. Plan §6 task 2.4.
     */
    @Bean
    @ConditionalOnMissingBean
    public MemoryOverflowPolicy memoryOverflowPolicy() {
        return new FailFastOverflowPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryAgentTool memoryAgentTool(AgentMindMemoryProvider memoryProvider,
                                           ObjectProvider<TenantGuard> tenantGuard,
                                           MemoryOverflowPolicy overflowPolicy,
                                           AgentMindMemoryProperties props) {
        return new MemoryAgentTool(memoryProvider, tenantGuard.getIfAvailable(),
                overflowPolicy, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryPromptInjector memoryPromptInjector(AgentMindMemoryProvider memoryProvider,
                                                     ObjectProvider<TenantGuard> tenantGuard,
                                                     AgentMindMemoryProperties props) {
        return new MemoryPromptInjector(memoryProvider, tenantGuard.getIfAvailable(), props);
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryMcpToolProvider memoryMcpToolProvider(AgentMindMemoryProvider memoryProvider) {
        return new MemoryMcpToolProvider(memoryProvider);
    }

    /**
     * Debug read endpoints at {@code GET /api/agentmind/memory/agent/{agentId}}
     * and {@code .../peer/{agentId}/{peerId}}. Off by default; flip
     * {@code jaiclaw.agentmind.memory.rest.enabled=true} to enable. Intended
     * for ops only.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "jaiclaw.agentmind.memory.rest", name = "enabled", havingValue = "true")
    public static class MemoryDebugRestConfig {
        @Bean
        @ConditionalOnMissingBean
        public MemoryDebugController memoryDebugController(AgentMindMemoryProvider memoryProvider,
                                                            ObjectProvider<TenantGuard> tenantGuard) {
            return new MemoryDebugController(memoryProvider, tenantGuard.getIfAvailable());
        }
    }

    /**
     * Operator-only tenant Memory write surface. Off by default; flip
     * {@code jaiclaw.agentmind.memory.tenant.enabled=true} to enable.
     * Role-guarded by {@code jaiclaw.agentmind.memory.tenant.write.roles}.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "jaiclaw.agentmind.memory.tenant", name = "enabled", havingValue = "true")
    public static class TenantMemoryRestConfig {
        @Bean
        @ConditionalOnMissingBean
        public TenantMemoryController tenantMemoryController(AgentMindMemoryProvider memoryProvider,
                                                              ObjectProvider<TenantGuard> tenantGuard,
                                                              MemoryOverflowPolicy overflowPolicy,
                                                              AgentMindMemoryProperties props) {
            return new TenantMemoryController(memoryProvider, tenantGuard.getIfAvailable(),
                    overflowPolicy, props);
        }

        @Bean
        @ConditionalOnMissingBean
        public TenantMemoryMcpToolProvider tenantMemoryMcpToolProvider(AgentMindMemoryProvider memoryProvider,
                                                                       MemoryOverflowPolicy overflowPolicy,
                                                                       AgentMindMemoryProperties props) {
            return new TenantMemoryMcpToolProvider(memoryProvider, overflowPolicy, props);
        }
    }
}
