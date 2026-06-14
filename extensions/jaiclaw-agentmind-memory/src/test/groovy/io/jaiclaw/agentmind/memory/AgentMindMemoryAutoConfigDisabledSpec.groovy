package io.jaiclaw.agentmind.memory

import io.jaiclaw.core.agent.AgentMindMemoryProvider
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

/**
 * Plan §6 cross-cutting "off by default" invariant for the Memory pillar.
 */
class AgentMindMemoryAutoConfigDisabledSpec extends Specification {

    ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentMindMemoryAutoConfiguration))

    def "no enabled property means no agentmind memory beans"() {
        expect:
        runner.run { ctx ->
            assert !ctx.containsBean("agentmindMemoryModuleMarker")
            assert ctx.getBeanNamesForType(AgentMindMemoryProvider).length == 0
        }
    }

    def "enabled=false explicitly means no memory beans"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.memory.enabled=false").run { ctx ->
            assert !ctx.containsBean("agentmindMemoryModuleMarker")
        }
    }

    def "enabled=true wires the autoconfig + the AgentMindMemoryProvider bean"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.memory.enabled=true").run { ctx ->
            assert ctx.containsBean("agentmindMemoryModuleMarker")
            assert ctx.getBeanNamesForType(AgentMindMemoryProvider).length == 1
            AgentMindMemoryProperties props = ctx.getBean(AgentMindMemoryProperties)
            assert props.enabled()
            assert props.budgets().tenantChars() == 4096
            assert props.budgets().agentChars() == 2200
            assert props.budgets().peerChars() == 1375
        }
    }

    def "tenant defaults: enabled=false, agent-write=false, ADMIN+OPERATOR roles"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.memory.enabled=true").run { ctx ->
            AgentMindMemoryProperties props = ctx.getBean(AgentMindMemoryProperties)
            assert !props.tenant().enabled()
            assert !props.tenant().agentWriteEnabled()
            assert props.tenant().writeRoles() == ["ADMIN", "OPERATOR"]
        }
    }

    def "char budgets are overridable per scope"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.agentmind.memory.enabled=true",
                "jaiclaw.agentmind.memory.budgets.tenant-chars=8192",
                "jaiclaw.agentmind.memory.budgets.agent-chars=3000",
                "jaiclaw.agentmind.memory.budgets.peer-chars=2000"
        ).run { ctx ->
            AgentMindMemoryProperties props = ctx.getBean(AgentMindMemoryProperties)
            assert props.budgets().tenantChars() == 8192
            assert props.budgets().agentChars() == 3000
            assert props.budgets().peerChars() == 2000
        }
    }
}
