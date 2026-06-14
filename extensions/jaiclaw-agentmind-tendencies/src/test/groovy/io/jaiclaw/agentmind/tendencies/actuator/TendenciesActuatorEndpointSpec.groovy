package io.jaiclaw.agentmind.tendencies.actuator

import io.jaiclaw.agentmind.tendencies.cadence.TendenciesCadenceGate
import io.jaiclaw.agentmind.tendencies.cost.TendenciesTokenBudget
import io.jaiclaw.agentmind.tendencies.executor.StripedDialecticExecutor
import spock.lang.Specification

class TendenciesActuatorEndpointSpec extends Specification {

    TendenciesCadenceGate gate = Mock()
    StripedDialecticExecutor executor = Mock()
    TendenciesTokenBudget budget = new TendenciesTokenBudget(100_000L)

    TendenciesActuatorEndpoint endpoint = new TendenciesActuatorEndpoint(gate, executor, budget)

    def "root read returns gate + executor + cost sections"() {
        given:
        gate.stats() >> new TendenciesCadenceGate.Stats(11L, 3L)
        executor.stats() >> new StripedDialecticExecutor.Stats(20L, 1L, 2L, 5, 8)

        when:
        Map<String, Object> out = endpoint.read()

        then:
        out.cadenceGate.hits == 11L
        out.cadenceGate.misses == 3L
        out.executor.submitted == 20L
        out.executor.dropped == 1L
        out.executor.active == 2L
        out.executor.queuedTotal == 5
        out.executor.stripes == 8
        out.cost.dailyCap == 100_000L
    }

    def "per-tenant read adds a tenant snapshot block"() {
        given:
        gate.stats() >> new TendenciesCadenceGate.Stats(0L, 0L)
        executor.stats() >> new StripedDialecticExecutor.Stats(0L, 0L, 0L, 0, 0)
        budget.recordSpend("acme", 30_000L)

        when:
        Map<String, Object> out = endpoint.readTenant("acme")

        then:
        out.tenant.tenantId == "acme"
        out.tenant.spent == 30_000L
        out.tenant.remaining == 70_000L
    }

    def "unknown tenant returns zero spent"() {
        given:
        gate.stats() >> new TendenciesCadenceGate.Stats(0L, 0L)
        executor.stats() >> new StripedDialecticExecutor.Stats(0L, 0L, 0L, 0, 0)

        when:
        Map<String, Object> out = endpoint.readTenant("ghost")

        then:
        out.tenant.spent == 0L
        out.tenant.remaining == 100_000L
    }
}
