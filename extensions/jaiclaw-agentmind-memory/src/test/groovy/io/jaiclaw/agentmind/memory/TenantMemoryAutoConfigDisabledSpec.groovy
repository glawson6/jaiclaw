package io.jaiclaw.agentmind.memory

import io.jaiclaw.agentmind.memory.mcp.TenantMemoryMcpToolProvider
import io.jaiclaw.agentmind.memory.web.MemoryDebugController
import io.jaiclaw.agentmind.memory.web.TenantMemoryController
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

/**
 * Plan §6 cross-cutting rule: tenant-scope opt-in is independent of pillar
 * opt-in. Tenant beans only wire when BOTH pillar AND tenant toggles are on.
 */
class TenantMemoryAutoConfigDisabledSpec extends Specification {

    ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentMindMemoryAutoConfiguration))

    def "pillar=true alone does NOT create tenant beans"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.memory.enabled=true").run { ctx ->
            assert ctx.containsBean("agentmindMemoryModuleMarker")
            assert ctx.getBeanNamesForType(TenantMemoryController).length == 0
            assert ctx.getBeanNamesForType(TenantMemoryMcpToolProvider).length == 0
        }
    }

    def "pillar=true + tenant.enabled=false explicitly: still no tenant beans"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.agentmind.memory.enabled=true",
                "jaiclaw.agentmind.memory.tenant.enabled=false"
        ).run { ctx ->
            assert ctx.getBeanNamesForType(TenantMemoryController).length == 0
            assert ctx.getBeanNamesForType(TenantMemoryMcpToolProvider).length == 0
        }
    }

    def "pillar=true + tenant.enabled=true wires both tenant beans"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.agentmind.memory.enabled=true",
                "jaiclaw.agentmind.memory.tenant.enabled=true"
        ).run { ctx ->
            assert ctx.getBeanNamesForType(TenantMemoryController).length == 1
            assert ctx.getBeanNamesForType(TenantMemoryMcpToolProvider).length == 1
        }
    }

    def "pillar=false + tenant.enabled=true: no beans at all (pillar gates everything)"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.agentmind.memory.enabled=false",
                "jaiclaw.agentmind.memory.tenant.enabled=true"
        ).run { ctx ->
            assert !ctx.containsBean("agentmindMemoryModuleMarker")
            assert ctx.getBeanNamesForType(TenantMemoryController).length == 0
            assert ctx.getBeanNamesForType(TenantMemoryMcpToolProvider).length == 0
        }
    }

    def "rest.enabled=true wires the debug controller only when pillar is on"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.agentmind.memory.enabled=true",
                "jaiclaw.agentmind.memory.rest.enabled=true"
        ).run { ctx ->
            assert ctx.getBeanNamesForType(MemoryDebugController).length == 1
        }
    }

    def "rest.enabled=false is the default — no debug controller wired"() {
        expect:
        runner.withPropertyValues("jaiclaw.agentmind.memory.enabled=true").run { ctx ->
            assert ctx.getBeanNamesForType(MemoryDebugController).length == 0
        }
    }
}
