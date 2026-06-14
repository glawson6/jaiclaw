package io.jaiclaw.agentmind.tendencies

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

import java.time.Duration

class AgentMindTendenciesAutoConfigDisabledSpec extends Specification {

    ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentMindTendenciesAutoConfiguration))

    def "no enabled property means no tendencies beans"() {
        expect:
        runner.run { ctx ->
            assert !ctx.containsBean("agentmindTendenciesModuleMarker")
        }
    }

    def "enabled=false explicitly means no tendencies beans"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.tendencies.enabled=false").run { ctx ->
            assert !ctx.containsBean("agentmindTendenciesModuleMarker")
        }
    }

    def "enabled=true wires the autoconfig"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.tendencies.enabled=true").run { ctx ->
            assert ctx.containsBean("agentmindTendenciesModuleMarker")
            AgentMindTendenciesProperties props = ctx.getBean(AgentMindTendenciesProperties)
            assert props.enabled()
            assert props.provider() == "deterministic"
            assert props.cadence().minInterval() == Duration.ofMinutes(15)
            assert props.cadence().minTurns() == 5
            assert props.executor().queueDepth() == 4
            assert props.cost().dailyTokenCap() == 100_000L
        }
    }

    def "tenant defaults: enabled=false, daily rollup, majority provider"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.tendencies.enabled=true").run { ctx ->
            AgentMindTendenciesProperties props = ctx.getBean(AgentMindTendenciesProperties)
            assert !props.tenant().enabled()
            assert props.tenant().rollupCadence() == Duration.ofHours(24)
            assert props.tenant().rollupMinActiveUsers() == 3
            assert props.tenant().rollupProvider() == "majority"
        }
    }

    def "cadence and budget are overridable"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.agentmind.tendencies.enabled=true",
                "jaiclaw.agentmind.tendencies.cadence.min-interval=PT5M",
                "jaiclaw.agentmind.tendencies.cadence.min-turns=10",
                "jaiclaw.agentmind.tendencies.cost.daily-token-cap=500000"
        ).run { ctx ->
            AgentMindTendenciesProperties props = ctx.getBean(AgentMindTendenciesProperties)
            assert props.cadence().minInterval() == Duration.ofMinutes(5)
            assert props.cadence().minTurns() == 10
            assert props.cost().dailyTokenCap() == 500_000L
        }
    }
}
