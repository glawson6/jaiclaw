package io.jaiclaw.autoconfigure;

import io.jaiclaw.core.agent.AgentHookDispatcher;
import io.jaiclaw.core.agent.ContextCompactor;
import io.jaiclaw.core.agent.MemoryProvider;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.memory.CircuitBreakerMemorySearchManager;
import io.jaiclaw.memory.InMemorySearchManager;
import io.jaiclaw.memory.InMemoryToggleStore;
import io.jaiclaw.memory.MemoryCircuitBreaker;
import io.jaiclaw.memory.MemorySearchManager;
import io.jaiclaw.memory.MemoryToggleStore;
import io.jaiclaw.memory.VectorStoreSearchManager;
import io.jaiclaw.plugin.PluginRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Memory, circuit breakers, and core agent SPI adapters (hook dispatcher,
 * context compactor, memory provider).
 *
 * <p>Beans defined here:
 * <ul>
 *   <li>{@link VectorStoreSearchManager} when a Spring AI
 *       {@code VectorStore} bean is available.</li>
 *   <li>{@link InMemorySearchManager} as the default fallback.</li>
 *   <li>{@link MemoryToggleStore} ({@link InMemoryToggleStore} default).</li>
 *   <li>{@link MemoryCircuitBreaker} via Spring Cloud Circuit Breaker when
 *       available, or native Resilience4j fallback.</li>
 *   <li>{@link CircuitBreakerMemorySearchManager} {@code @Primary} wrapper
 *       when a circuit breaker bean is present.</li>
 *   <li>{@link AgentHookDispatcher} — plugin hook bridge.</li>
 *   <li>{@link ContextCompactor} — context window compaction adapter.</li>
 *   <li>{@link MemoryProvider} — workspace-memory adapter.</li>
 * </ul>
 *
 * <p>Runs after {@link JaiClawPluginAutoConfiguration} because the hook
 * dispatcher depends on {@link PluginRegistry}.
 *
 * <p>Carved out of the former {@code JaiClawAutoConfiguration} monolith
 * (audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.4, Phase 3 P3.4).
 */
@AutoConfiguration(after = JaiClawPluginAutoConfiguration.class)
public class JaiClawMemoryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawMemoryAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(MemorySearchManager.class)
    @ConditionalOnClass(name = "org.springframework.ai.vectorstore.VectorStore")
    @ConditionalOnBean(type = "org.springframework.ai.vectorstore.VectorStore")
    public VectorStoreSearchManager vectorStoreSearchManager(
            org.springframework.ai.vectorstore.VectorStore vectorStore,
            TenantGuard tenantGuard) {
        return new VectorStoreSearchManager(vectorStore, tenantGuard);
    }

    @Bean
    @ConditionalOnMissingBean(MemorySearchManager.class)
    public InMemorySearchManager inMemorySearchManager(TenantGuard tenantGuard) {
        return new InMemorySearchManager(tenantGuard);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryToggleStore.class)
    public MemoryToggleStore memoryToggleStore() {
        return new InMemoryToggleStore();
    }

    /**
     * Spring Cloud CircuitBreaker integration — preferred when available.
     * Uses {@code CircuitBreakerFactory.create("vectorStore")} for auto Micrometer integration.
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory")
    static class SpringCloudCircuitBreakerConfiguration {

        @Bean
        @ConditionalOnMissingBean(MemoryCircuitBreaker.class)
        public MemoryCircuitBreaker springCloudMemoryCircuitBreaker(
                org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory<?, ?> factory) {
            return new io.jaiclaw.memory.SpringCloudCircuitBreaker(factory.create("vectorStore"));
        }
    }

    /**
     * Native Resilience4j fallback — used when Spring Cloud CircuitBreaker is not present.
     */
    @Configuration
    @ConditionalOnClass(name = "io.github.resilience4j.circuitbreaker.CircuitBreaker")
    @ConditionalOnMissingBean(MemoryCircuitBreaker.class)
    static class NativeResilience4jCircuitBreakerConfiguration {

        @Bean
        public MemoryCircuitBreaker nativeMemoryCircuitBreaker() {
            return new io.jaiclaw.memory.NativeResilience4jCircuitBreaker();
        }
    }

    /**
     * Wraps the MemorySearchManager with circuit breaker protection when a
     * {@link MemoryCircuitBreaker} bean is available.
     */
    @Bean
    @Primary
    @ConditionalOnBean(MemoryCircuitBreaker.class)
    public CircuitBreakerMemorySearchManager circuitBreakerMemorySearchManager(
            MemorySearchManager delegate,
            MemoryToggleStore toggleStore,
            MemoryCircuitBreaker circuitBreaker) {
        log.info("Wrapping MemorySearchManager with circuit breaker protection");
        return new CircuitBreakerMemorySearchManager(delegate, toggleStore, circuitBreaker);
    }

    // --- SPI adapter beans (plugin/compaction/memory bridges) ---

    @Bean
    @ConditionalOnMissingBean(AgentHookDispatcher.class)
    @ConditionalOnClass(name = "io.jaiclaw.plugin.HookRunnerAdapter")
    public AgentHookDispatcher agentHookDispatcher(PluginRegistry pluginRegistry) {
        var hookRunner = new io.jaiclaw.plugin.HookRunner(pluginRegistry);
        return new io.jaiclaw.plugin.HookRunnerAdapter(hookRunner);
    }

    @Bean
    @ConditionalOnMissingBean(ContextCompactor.class)
    @ConditionalOnClass(name = "io.jaiclaw.compaction.CompactionServiceAdapter")
    public ContextCompactor contextCompactor(
            ObjectProvider<io.jaiclaw.compaction.TokenEstimator> tokenEstimatorProvider) {
        var config = io.jaiclaw.core.model.CompactionConfig.DEFAULT;
        io.jaiclaw.compaction.TokenEstimator estimator = tokenEstimatorProvider.getIfAvailable();
        var service = estimator != null
                ? new io.jaiclaw.compaction.CompactionService(config, estimator)
                : new io.jaiclaw.compaction.CompactionService(config);
        return new io.jaiclaw.compaction.CompactionServiceAdapter(service);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryProvider.class)
    @ConditionalOnClass(name = "io.jaiclaw.memory.WorkspaceMemoryProvider")
    public MemoryProvider memoryProvider(TenantGuard tenantGuard) {
        return new io.jaiclaw.memory.WorkspaceMemoryProvider(tenantGuard);
    }
}
