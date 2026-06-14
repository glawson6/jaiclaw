package io.jaiclaw.agentmind.tendencies.actuator;

import io.jaiclaw.agentmind.tendencies.cadence.TendenciesCadenceGate;
import io.jaiclaw.agentmind.tendencies.cost.TendenciesTokenBudget;
import io.jaiclaw.agentmind.tendencies.executor.StripedDialecticExecutor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom Spring Boot Actuator endpoint that surfaces operational state
 * for the Tendencies pipeline at {@code /actuator/agentmind/tendencies}.
 *
 * <p>Reports:
 * <ul>
 *   <li>Cadence-gate hits / misses (aggregate)</li>
 *   <li>Striped executor submitted / dropped / active / queued totals</li>
 *   <li>Per-tenant token-budget snapshots (when a tenant id is selected
 *       via the path; aggregate cap shown at the root)</li>
 * </ul>
 *
 * <p>Plan §8 task 3.12.
 */
@Endpoint(id = "agentmind-tendencies")
public class TendenciesActuatorEndpoint {

    private final TendenciesCadenceGate cadenceGate;
    private final StripedDialecticExecutor executor;
    private final TendenciesTokenBudget tokenBudget;

    public TendenciesActuatorEndpoint(TendenciesCadenceGate cadenceGate,
                                       StripedDialecticExecutor executor,
                                       TendenciesTokenBudget tokenBudget) {
        this.cadenceGate = cadenceGate;
        this.executor = executor;
        this.tokenBudget = tokenBudget;
    }

    @ReadOperation
    public Map<String, Object> read() {
        Map<String, Object> out = new LinkedHashMap<>();
        TendenciesCadenceGate.Stats gateStats = cadenceGate.stats();
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("hits", gateStats.hits());
        gate.put("misses", gateStats.misses());
        out.put("cadenceGate", gate);

        StripedDialecticExecutor.Stats execStats = executor.stats();
        Map<String, Object> exec = new LinkedHashMap<>();
        exec.put("submitted", execStats.submitted());
        exec.put("dropped", execStats.dropped());
        exec.put("active", execStats.active());
        exec.put("queuedTotal", execStats.queuedTotal());
        exec.put("stripes", execStats.stripes());
        out.put("executor", exec);

        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("dailyCap", tokenBudget.dailyCap());
        out.put("cost", cost);
        return out;
    }

    @ReadOperation
    public Map<String, Object> readTenant(@Selector String tenantId) {
        Map<String, Object> out = read();
        TendenciesTokenBudget.Snapshot snap = tokenBudget.snapshot(tenantId);
        Map<String, Object> tenant = new LinkedHashMap<>();
        tenant.put("tenantId", snap.tenantId());
        tenant.put("dailyCap", snap.dailyCap());
        tenant.put("spent", snap.spent());
        tenant.put("remaining", snap.remaining());
        out.put("tenant", tenant);
        return out;
    }
}
