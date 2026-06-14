package io.jaiclaw.agentmind.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jaiclaw.core.agent.AgentMindMemoryProvider;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.agentmind.memory.overflow.FailFastOverflowPolicy;
import io.jaiclaw.agentmind.memory.overflow.MemoryOverflowPolicy;
import io.jaiclaw.agentmind.memory.store.BoundedBlobMemoryStore;
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
        m.registerModule(new JavaTimeModule());
        m.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return m;
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentMindMemoryProvider agentmindMemoryProvider(AgentMindMemoryProperties props,
                                                      ObjectMapper mapper,
                                                      ObjectProvider<TenantGuard> tenantGuard) {
        return new BoundedBlobMemoryStore(Path.of(props.rootDir()), tenantGuard.getIfAvailable(), mapper);
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
}
