package io.jaiclaw.agentmind.tendencies

import io.jaiclaw.agentmind.tendencies.actuator.TendenciesActuatorEndpoint
import io.jaiclaw.agentmind.tendencies.cadence.TendenciesCadenceGate
import io.jaiclaw.agentmind.tendencies.cost.TendenciesTokenBudget
import io.jaiclaw.agentmind.tendencies.executor.StripedDialecticExecutor
import io.jaiclaw.agentmind.tendencies.hook.TendenciesDialecticTrigger
import io.jaiclaw.agentmind.tendencies.hook.TendenciesUserMessageInjector
import io.jaiclaw.agentmind.tendencies.learning.DeterministicTendenciesProvider
import io.jaiclaw.agentmind.tendencies.learning.TendenciesLearningProvider
import io.jaiclaw.agentmind.tendencies.mcp.TendenciesMcpToolProvider
import io.jaiclaw.agentmind.tendencies.transcript.InMemoryTranscriptSource
import io.jaiclaw.agentmind.tendencies.transcript.TranscriptSource
import io.jaiclaw.agentmind.tendencies.web.TendenciesDebugController
import io.jaiclaw.core.agent.TendenciesStoreProvider
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

/**
 * Verifies the full bean graph wires up correctly when the pillar is
 * enabled. Covers plan §8 task 3.14 (autoconfig wiring) — the cross-
 * cutting "tenant-scope opt-in is independent of pillar opt-in" rule
 * is tested separately by TenantTendenciesAutoConfigDisabledSpec in
 * Phase 5; pre-Phase-5 the tenant beans are simply not in scope.
 */
class TendenciesAutoConfigBeanWiringSpec extends Specification {

    ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentMindTendenciesAutoConfiguration))

    def "pillar=true wires all core beans"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.tendencies.enabled=true").run { ctx ->
            assert ctx.getBean(TendenciesStoreProvider) != null
            assert ctx.getBean(TendenciesCadenceGate) != null
            assert ctx.getBean(StripedDialecticExecutor) != null
            assert ctx.getBean(TendenciesLearningProvider) instanceof DeterministicTendenciesProvider
            assert ctx.getBean(TendenciesTokenBudget) != null
            assert ctx.getBean(TranscriptSource) instanceof InMemoryTranscriptSource
            assert ctx.getBean(TendenciesUserMessageInjector) != null
            assert ctx.getBean(TendenciesDialecticTrigger) != null
            assert ctx.getBean(TendenciesMcpToolProvider) != null
            assert ctx.getBean(TendenciesActuatorEndpoint) != null
        }
    }

    def "REST debug controller is off-by-default"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.tendencies.enabled=true").run { ctx ->
            assert ctx.getBeanNamesForType(TendenciesDebugController).length == 0
        }
    }

    def "REST debug controller wires when rest.enabled=true"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.agentmind.tendencies.enabled=true",
                "jaiclaw.agentmind.tendencies.rest.enabled=true"
        ).run { ctx ->
            assert ctx.getBeanNamesForType(TendenciesDebugController).length == 1
        }
    }

    def "pillar=false: REST controller is absent even if rest.enabled=true"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.agentmind.tendencies.enabled=false",
                "jaiclaw.agentmind.tendencies.rest.enabled=true"
        ).run { ctx ->
            assert ctx.getBeanNamesForType(TendenciesDebugController).length == 0
            assert ctx.getBeanNamesForType(TendenciesActuatorEndpoint).length == 0
        }
    }
}
