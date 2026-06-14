package io.jaiclaw.hermes.soul

import io.jaiclaw.hermes.soul.mcp.TenantSoulMcpToolProvider
import io.jaiclaw.hermes.soul.web.TenantSoulController
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

/**
 * Plan §6 cross-cutting rule (analysis §6): tenant-scope opt-in is
 * independent of pillar opt-in. The pillar can be on with tenant beans
 * absent; both must be on for tenant write surfaces to wire.
 */
class TenantSoulAutoConfigDisabledSpec extends Specification {

    ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HermesSoulAutoConfiguration))

    def "pillar=true alone does NOT create tenant beans"() {
        expect:
        runner.withPropertyValues("jaiclaw.hermes.soul.enabled=true").run { ctx ->
            assert ctx.containsBean("hermesSoulModuleMarker")
            assert ctx.getBeanNamesForType(TenantSoulController).length == 0
            assert ctx.getBeanNamesForType(TenantSoulMcpToolProvider).length == 0
        }
    }

    def "pillar=true + tenant.enabled=false explicitly: still no tenant beans"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.hermes.soul.enabled=true",
                "jaiclaw.hermes.soul.tenant.enabled=false"
        ).run { ctx ->
            assert ctx.getBeanNamesForType(TenantSoulController).length == 0
            assert ctx.getBeanNamesForType(TenantSoulMcpToolProvider).length == 0
        }
    }

    def "pillar=true + tenant.enabled=true wires both tenant beans"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.hermes.soul.enabled=true",
                "jaiclaw.hermes.soul.tenant.enabled=true"
        ).run { ctx ->
            assert ctx.getBeanNamesForType(TenantSoulController).length == 1
            assert ctx.getBeanNamesForType(TenantSoulMcpToolProvider).length == 1
        }
    }

    def "pillar=false + tenant.enabled=true: no beans at all (pillar gates everything)"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.hermes.soul.enabled=false",
                "jaiclaw.hermes.soul.tenant.enabled=true"
        ).run { ctx ->
            assert !ctx.containsBean("hermesSoulModuleMarker")
            assert ctx.getBeanNamesForType(TenantSoulController).length == 0
            assert ctx.getBeanNamesForType(TenantSoulMcpToolProvider).length == 0
        }
    }

    def "rest.enabled=true wires the SoulDebugController only when pillar is on"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.hermes.soul.enabled=true",
                "jaiclaw.hermes.soul.rest.enabled=true"
        ).run { ctx ->
            assert ctx.getBeanNamesForType(io.jaiclaw.hermes.soul.web.SoulDebugController).length == 1
        }
    }

    def "rest.enabled=false is the default — no debug controller wired"() {
        expect:
        runner.withPropertyValues("jaiclaw.hermes.soul.enabled=true").run { ctx ->
            assert ctx.getBeanNamesForType(io.jaiclaw.hermes.soul.web.SoulDebugController).length == 0
        }
    }
}
