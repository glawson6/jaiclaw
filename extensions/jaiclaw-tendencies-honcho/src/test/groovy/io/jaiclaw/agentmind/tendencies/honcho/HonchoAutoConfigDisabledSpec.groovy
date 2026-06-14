package io.jaiclaw.agentmind.tendencies.honcho

import io.jaiclaw.agentmind.tendencies.learning.TendenciesLearningProvider
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

/**
 * Plan §8 task 4.1 — verifies the Honcho sub-module is off by default
 * and only activates when the consumer opts in.
 */
class HonchoAutoConfigDisabledSpec extends Specification {

    ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HonchoAutoConfiguration))

    def "no provider property → no honcho beans"() {
        expect:
        runner.run { ctx ->
            assert ctx.getBeanNamesForType(HonchoClient).length == 0
            assert ctx.getBeanNamesForType(TendenciesLearningProvider).length == 0
        }
    }

    def "provider=deterministic → no honcho beans (honcho is for the honcho provider only)"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.tendencies.provider=deterministic").run { ctx ->
            assert ctx.getBeanNamesForType(HonchoClient).length == 0
            assert ctx.getBeanNamesForType(TendenciesLearningProvider).length == 0
        }
    }

    def "provider=honcho with no client → NoOpHonchoClient fallback + HonchoRemoteTendenciesProvider"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.tendencies.provider=honcho").run { ctx ->
            assert ctx.getBean(HonchoClient) instanceof NoOpHonchoClient
            assert ctx.getBean(TendenciesLearningProvider) instanceof HonchoRemoteTendenciesProvider
        }
    }
}
