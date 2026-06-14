package io.jaiclaw.agentmind.soul

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

/**
 * Plan §6 cross-cutting "off by default" invariant.
 *
 * <p>With no property set, the autoconfig must produce zero beans and the
 * configuration class itself must not be created. Reviewers reject merges
 * that break this contract.
 */
class SoulAutoConfigDisabledSpec extends Specification {

    ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentMindSoulAutoConfiguration))

    def "no enabled property means no agentmind soul beans"() {
        expect:
        runner.run { ctx ->
            assert !ctx.containsBean("agentmindSoulModuleMarker")
            assert ctx.getBeanNamesForType(AgentMindSoulAutoConfiguration).length == 0
        }
    }

    def "enabled=false explicitly means no agentmind soul beans"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.soul.enabled=false").run { ctx ->
            assert !ctx.containsBean("agentmindSoulModuleMarker")
        }
    }

    def "enabled=true wires the autoconfig"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.soul.enabled=true").run { ctx ->
            assert ctx.containsBean("agentmindSoulModuleMarker")
            assert ctx.getBean(AgentMindSoulProperties).enabled()
        }
    }

    def "tenant.enabled defaults false even when pillar enabled"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.soul.enabled=true").run { ctx ->
            AgentMindSoulProperties props = ctx.getBean(AgentMindSoulProperties)
            assert !props.tenant().enabled()
            assert props.tenant().writeRoles() == ["ADMIN", "OPERATOR"]
        }
    }

    def "tenant.enabled=true loads with default role guard"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.agentmind.soul.enabled=true",
                "jaiclaw.agentmind.soul.tenant.enabled=true"
        ).run { ctx ->
            AgentMindSoulProperties props = ctx.getBean(AgentMindSoulProperties)
            assert props.tenant().enabled()
            assert props.tenant().writeRoles() == ["ADMIN", "OPERATOR"]
        }
    }
}
