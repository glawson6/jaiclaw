package io.jaiclaw.agentmind.memory.metrics;

import io.jaiclaw.core.agent.AgentMindMemoryProvider;
import io.jaiclaw.core.agent.MemoryOverflowException;
import io.jaiclaw.core.agent.StaleMemoryVersionException;
import io.jaiclaw.core.model.MemoryDocument;
import io.jaiclaw.core.model.MemoryScope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Optional;

/**
 * Decorator around {@link AgentMindMemoryProvider} that publishes Micrometer
 * counters for writes, write conflicts, and overflow rejections. Sits
 * between consumers (agent tool, prompt injector, REST controllers, MCP
 * providers) and the concrete file/H2/Redis backend.
 *
 * <p>Metrics emitted:
 *
 * <ul>
 *   <li>{@code jaiclaw.memory.writes{scope, action, outcome}} — write
 *       attempts tagged by scope (TENANT|AGENT|PEER), action (save|delete),
 *       and outcome (success|conflict|overflow).</li>
 *   <li>{@code jaiclaw.memory.overflows{scope}} — subset of
 *       writes{outcome=overflow} for ease of alert-rule authoring.</li>
 *   <li>{@code jaiclaw.agentmind.memory.conflicts{scope}} — subset of
 *       writes{outcome=conflict} for the same reason.</li>
 * </ul>
 *
 * <p>Reads are not metered — the prompt injector reads once per session and
 * would otherwise dominate the dashboard signal.
 *
 * <p>Plan §6 task 2.12.
 */
public class InstrumentedMemoryProvider implements AgentMindMemoryProvider {

    private final AgentMindMemoryProvider delegate;
    private final MeterRegistry registry;

    public InstrumentedMemoryProvider(AgentMindMemoryProvider delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public Optional<MemoryDocument> findMemory(String tenantId, MemoryScope scope,
                                                String agentId, String peerId) {
        return delegate.findMemory(tenantId, scope, agentId, peerId);
    }

    @Override
    public MemoryDocument saveMemory(MemoryDocument document) {
        try {
            MemoryDocument saved = delegate.saveMemory(document);
            writes(document.scope(), "save", "success").increment();
            return saved;
        } catch (MemoryOverflowException e) {
            writes(document.scope(), "save", "overflow").increment();
            overflows(document.scope()).increment();
            throw e;
        } catch (StaleMemoryVersionException e) {
            writes(document.scope(), "save", "conflict").increment();
            conflicts(document.scope()).increment();
            throw e;
        }
    }

    @Override
    public void deleteMemory(String tenantId, MemoryScope scope, String agentId, String peerId) {
        delegate.deleteMemory(tenantId, scope, agentId, peerId);
        writes(scope, "delete", "success").increment();
    }

    private Counter writes(MemoryScope scope, String action, String outcome) {
        return Counter.builder("jaiclaw.memory.writes")
                .description("Memory write attempts by scope, action, and outcome")
                .tag("scope", scope.name())
                .tag("action", action)
                .tag("outcome", outcome)
                .register(registry);
    }

    private Counter overflows(MemoryScope scope) {
        return Counter.builder("jaiclaw.memory.overflows")
                .description("Memory write rejections due to char-budget overflow")
                .tag("scope", scope.name())
                .register(registry);
    }

    private Counter conflicts(MemoryScope scope) {
        return Counter.builder("jaiclaw.agentmind.memory.conflicts")
                .description("Memory write rejections due to optimistic-CAS version mismatch")
                .tag("scope", scope.name())
                .register(registry);
    }
}
