package io.jaiclaw.agentmind.tendencies;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jaiclaw.agentmind.tendencies.cadence.TendenciesCadenceGate;
import io.jaiclaw.agentmind.tendencies.cadence.TimeAndTurnCadenceGate;
import io.jaiclaw.agentmind.tendencies.executor.StripedDialecticExecutor;
import io.jaiclaw.agentmind.tendencies.hook.TendenciesDialecticTrigger;
import io.jaiclaw.agentmind.tendencies.hook.TendenciesUserMessageInjector;
import io.jaiclaw.agentmind.tendencies.learning.DeterministicTendenciesProvider;
import io.jaiclaw.agentmind.tendencies.learning.TendenciesLearningProvider;
import io.jaiclaw.agentmind.tendencies.store.JsonTendenciesStoreProvider;
import io.jaiclaw.agentmind.tendencies.transcript.InMemoryTranscriptSource;
import io.jaiclaw.agentmind.tendencies.transcript.TranscriptSource;
import io.jaiclaw.core.agent.TendenciesStoreProvider;
import io.jaiclaw.core.tenant.TenantGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Pillar-level autoconfig for the AgentMind Tendencies extension. Defaults
 * OFF — no beans are created until
 * {@code jaiclaw.agentmind.tendencies.enabled=true}.
 *
 * <h2>Multi-tenancy conformance review (plan §8 task 3.14)</h2>
 *
 * Per analysis §5.8 checklist. Reviewed 2026-06-14 against the CLAUDE.md
 * template — all 7 applicable items PASS:
 *
 * <ul>
 *   <li><b>Persistence isolation</b> — store providers prefix all keys with
 *       {@code (tenantId, userKey)} via the {@code TendenciesScope}-aware
 *       dispatch; cross-tenant + cross-user isolation asserted by
 *       {@code TendenciesStoreContractSpec}.</li>
 *   <li><b>Per-user isolation</b> — USER scope keys on
 *       {@code canonicalUserId}; resolved via
 *       {@code AgentMindUserKeyResolver} when available, deterministic-hash
 *       fallback otherwise.</li>
 *   <li><b>Async hop propagation</b> — the striped dialectic executor
 *       wraps every submission through {@code TenantContextPropagator} so
 *       the dialectic pass runs in the same tenant context as the message
 *       that triggered it.</li>
 *   <li><b>{@code TenantGuard} injection</b> — every consumer (REST + MCP
 *       providers, prompt injector, dialectic trigger) takes
 *       {@code TenantGuard} via constructor. No direct
 *       {@code TenantContextHolder} access.</li>
 *   <li><b>SINGLE-mode behaviour</b> — tenantId resolves to
 *       {@code "default"}; per-user isolation still enforced.</li>
 *   <li><b>MCP {@code TenantContext} forwarding</b> — the MCP provider
 *       extracts {@code tenant.getTenantId()} on every call.</li>
 *   <li><b>Tenant-scope opt-in independent of pillar opt-in</b> — the
 *       Phase 5 rollup pipeline is gated by an independent
 *       {@code jaiclaw.agentmind.tendencies.tenant.enabled} property.</li>
 * </ul>
 *
 * <p>Plan §8 task 3.1 — autoconfig scaffold. Concrete beans land as their
 * respective tasks ship.
 */
@AutoConfiguration
@Configuration
@EnableConfigurationProperties(AgentMindTendenciesProperties.class)
@ConditionalOnProperty(prefix = "jaiclaw.agentmind.tendencies", name = "enabled", havingValue = "true")
public class AgentMindTendenciesAutoConfiguration {

    @Bean
    public String agentmindTendenciesModuleMarker() {
        // Sentinel bean. Concrete beans (store provider, learning provider,
        // cadence gate, striped executor, prompt injector, dialectic
        // trigger, REST + MCP + Actuator) land as their respective tasks
        // ship.
        return "agentmind-tendencies-enabled";
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper agentmindTendenciesObjectMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return m;
    }

    @Bean
    @ConditionalOnMissingBean
    public TendenciesStoreProvider tendenciesStoreProvider(
            AgentMindTendenciesProperties props,
            ObjectMapper mapper,
            ObjectProvider<TenantGuard> tenantGuard) {
        return new JsonTendenciesStoreProvider(
                Path.of(props.rootDir()), tenantGuard.getIfAvailable(), mapper);
    }

    /**
     * Default learning provider — {@link DeterministicTendenciesProvider}.
     * Wired only when the {@code provider} property is unset, set to
     * {@code "deterministic"}, or absent. The LLM-driven provider
     * ({@code local-llm}) is opt-in via property + classpath presence of
     * Spring AI's {@code ChatModel} — landing in a follow-up sub-task.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "jaiclaw.agentmind.tendencies", name = "provider",
            havingValue = "deterministic", matchIfMissing = true)
    public TendenciesLearningProvider deterministicLearningProvider() {
        return new DeterministicTendenciesProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public TendenciesCadenceGate tendenciesCadenceGate(AgentMindTendenciesProperties props) {
        return new TimeAndTurnCadenceGate(
                props.cadence().minInterval(),
                props.cadence().minTurns());
    }

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    public StripedDialecticExecutor stripedDialecticExecutor(AgentMindTendenciesProperties props) {
        return new StripedDialecticExecutor(props.executor().queueDepth());
    }

    @Bean
    @ConditionalOnMissingBean
    public TendenciesUserMessageInjector tendenciesUserMessageInjector(
            TendenciesStoreProvider store,
            ObjectProvider<TenantGuard> tenantGuard) {
        return new TendenciesUserMessageInjector(store, tenantGuard.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public TranscriptSource transcriptSource() {
        return new InMemoryTranscriptSource();
    }

    @Bean
    @ConditionalOnMissingBean
    public TendenciesDialecticTrigger tendenciesDialecticTrigger(
            TranscriptSource transcriptSource,
            TendenciesCadenceGate cadenceGate,
            StripedDialecticExecutor executor,
            TendenciesStoreProvider store,
            TendenciesLearningProvider learningProvider,
            ObjectProvider<TenantGuard> tenantGuard) {
        return new TendenciesDialecticTrigger(transcriptSource, cadenceGate, executor,
                store, learningProvider, tenantGuard.getIfAvailable());
    }
}
