package io.jaiclaw.hermes.soul.metrics;

import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.agent.StaleSoulVersionException;
import io.jaiclaw.core.model.Soul;
import io.jaiclaw.core.model.SoulScope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Optional;

/**
 * Decorator around {@link SoulProvider} that publishes Micrometer counters
 * for writes and write conflicts. Sits between {@code SoulProvider} consumers
 * (prompt injector, agent tool, REST controllers, MCP providers) and the
 * concrete file/H2/Redis backend.
 *
 * <p>Metrics emitted:
 *
 * <ul>
 *   <li>{@code jaiclaw.soul.writes{scope, action, outcome}} — write attempts,
 *       tagged by scope (TENANT|AGENT), action (save|delete), and outcome
 *       (success|conflict).</li>
 *   <li>{@code jaiclaw.hermes.soul.conflicts{scope}} — stale-version
 *       rejections by scope. Subset of the writes counter for ease of alert
 *       rule authoring.</li>
 * </ul>
 *
 * <p>Plan §5 task 1.11. The {@code jaiclaw.soul.size.bytes} gauge planned for
 * this task is deferred — gauges need a polling cadence and the file backend
 * does not currently scan disk on read; the agent + REST surfaces will emit
 * a per-write size histogram in a follow-up task to avoid the gauge polling
 * cost.
 */
public class InstrumentedSoulProvider implements SoulProvider {

    private final SoulProvider delegate;
    private final MeterRegistry registry;

    public InstrumentedSoulProvider(SoulProvider delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public Optional<Soul> findSoul(String tenantId, SoulScope scope, String agentId) {
        // Reads are not metered to avoid drowning out the write signal in
        // dashboards. The prompt injector path reads once per session and
        // would otherwise dominate.
        return delegate.findSoul(tenantId, scope, agentId);
    }

    @Override
    public Soul saveSoul(Soul soul) {
        try {
            Soul saved = delegate.saveSoul(soul);
            writes(soul.scope(), "save", "success").increment();
            return saved;
        } catch (StaleSoulVersionException e) {
            writes(soul.scope(), "save", "conflict").increment();
            conflicts(soul.scope()).increment();
            throw e;
        }
    }

    @Override
    public void deleteSoul(String tenantId, SoulScope scope, String agentId) {
        delegate.deleteSoul(tenantId, scope, agentId);
        writes(scope, "delete", "success").increment();
    }

    private Counter writes(SoulScope scope, String action, String outcome) {
        return Counter.builder("jaiclaw.soul.writes")
                .description("Soul write attempts by scope, action, and outcome")
                .tag("scope", scope.name())
                .tag("action", action)
                .tag("outcome", outcome)
                .register(registry);
    }

    private Counter conflicts(SoulScope scope) {
        return Counter.builder("jaiclaw.hermes.soul.conflicts")
                .description("Soul write rejections due to optimistic-CAS version mismatch")
                .tag("scope", scope.name())
                .register(registry);
    }
}
