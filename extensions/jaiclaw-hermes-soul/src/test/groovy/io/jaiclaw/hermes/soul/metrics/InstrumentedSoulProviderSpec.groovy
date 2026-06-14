package io.jaiclaw.hermes.soul.metrics

import io.jaiclaw.core.agent.SoulProvider
import io.jaiclaw.core.agent.StaleSoulVersionException
import io.jaiclaw.core.model.Soul
import io.jaiclaw.core.model.SoulScope
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.util.Optional

class InstrumentedSoulProviderSpec extends Specification {

    SoulProvider delegate = Mock()
    SimpleMeterRegistry registry = new SimpleMeterRegistry()
    InstrumentedSoulProvider instrumented = new InstrumentedSoulProvider(delegate, registry)

    double counter(String name, Map<String, String> tags) {
        def search = registry.find(name)
        tags.each { k, v -> search = search.tag(k, v) }
        def c = search.counter()
        return c == null ? 0.0d : c.count()
    }

    def "successful save increments writes{scope,action=save,outcome=success}"() {
        given:
        Soul soul = Soul.forAgent("acme", "bot", "x")
        delegate.saveSoul(_) >> { args -> args[0] }

        when:
        instrumented.saveSoul(soul)

        then:
        counter("jaiclaw.soul.writes",
                [scope: "AGENT", action: "save", outcome: "success"]) == 1.0d
        counter("jaiclaw.soul.writes",
                [scope: "AGENT", action: "save", outcome: "conflict"]) == 0.0d
    }

    def "stale save increments writes{outcome=conflict} AND conflicts"() {
        given:
        Soul soul = Soul.forAgent("acme", "bot", "x")
        delegate.saveSoul(_) >> { throw new StaleSoulVersionException(5L, 3L) }

        when:
        instrumented.saveSoul(soul)

        then:
        thrown(StaleSoulVersionException)
        counter("jaiclaw.soul.writes",
                [scope: "AGENT", action: "save", outcome: "conflict"]) == 1.0d
        counter("jaiclaw.hermes.soul.conflicts", [scope: "AGENT"]) == 1.0d
    }

    def "TENANT-scope writes are tagged separately from AGENT-scope writes"() {
        given:
        delegate.saveSoul(_) >> { args -> args[0] }

        when:
        instrumented.saveSoul(Soul.forTenant("acme", "x"))
        instrumented.saveSoul(Soul.forAgent("acme", "bot", "x"))

        then:
        counter("jaiclaw.soul.writes",
                [scope: "TENANT", action: "save", outcome: "success"]) == 1.0d
        counter("jaiclaw.soul.writes",
                [scope: "AGENT", action: "save", outcome: "success"]) == 1.0d
    }

    def "delete increments writes{action=delete}"() {
        when:
        instrumented.deleteSoul("acme", SoulScope.AGENT, "bot")

        then:
        1 * delegate.deleteSoul("acme", SoulScope.AGENT, "bot")
        counter("jaiclaw.soul.writes",
                [scope: "AGENT", action: "delete", outcome: "success"]) == 1.0d
    }

    def "reads are NOT metered (no read counter created)"() {
        given:
        delegate.findSoul(_, _, _) >> Optional.empty()

        when:
        instrumented.findSoul("acme", SoulScope.AGENT, "bot")

        then:
        1 * delegate.findSoul(_, _, _) >> Optional.empty()
        registry.find("jaiclaw.soul.reads").counter() == null
    }
}
