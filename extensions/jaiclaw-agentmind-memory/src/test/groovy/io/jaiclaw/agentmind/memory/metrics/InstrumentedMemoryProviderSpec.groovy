package io.jaiclaw.agentmind.memory.metrics

import io.jaiclaw.core.agent.AgentMindMemoryProvider
import io.jaiclaw.core.agent.MemoryOverflowException
import io.jaiclaw.core.agent.StaleMemoryVersionException
import io.jaiclaw.core.model.MemoryDocument
import io.jaiclaw.core.model.MemoryScope
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.util.Optional

class InstrumentedMemoryProviderSpec extends Specification {

    AgentMindMemoryProvider delegate = Mock()
    SimpleMeterRegistry registry = new SimpleMeterRegistry()
    InstrumentedMemoryProvider instrumented = new InstrumentedMemoryProvider(delegate, registry)

    double counter(String name, Map<String, String> tags) {
        def search = registry.find(name)
        tags.each { k, v -> search = search.tag(k, v) }
        def c = search.counter()
        return c == null ? 0.0d : c.count()
    }

    def "successful save increments writes{outcome=success}"() {
        given:
        MemoryDocument doc = MemoryDocument.forAgent("acme", "bot", "x", 2200)
        delegate.saveMemory(_) >> { args -> args[0] }

        when:
        instrumented.saveMemory(doc)

        then:
        counter("jaiclaw.memory.writes",
                [scope: "AGENT", action: "save", outcome: "success"]) == 1.0d
    }

    def "stale save increments writes{outcome=conflict} AND conflicts"() {
        given:
        MemoryDocument doc = MemoryDocument.forAgent("acme", "bot", "x", 2200)
        delegate.saveMemory(_) >> { throw new StaleMemoryVersionException(5L, 3L) }

        when:
        instrumented.saveMemory(doc)

        then:
        thrown(StaleMemoryVersionException)
        counter("jaiclaw.memory.writes",
                [scope: "AGENT", action: "save", outcome: "conflict"]) == 1.0d
        counter("jaiclaw.agentmind.memory.conflicts", [scope: "AGENT"]) == 1.0d
    }

    def "overflow save increments writes{outcome=overflow} AND overflows"() {
        given:
        MemoryDocument doc = MemoryDocument.forAgent("acme", "bot", "y" * 100, 50)
        delegate.saveMemory(_) >> { throw new MemoryOverflowException(MemoryScope.AGENT, 50, 100) }

        when:
        instrumented.saveMemory(doc)

        then:
        thrown(MemoryOverflowException)
        counter("jaiclaw.memory.writes",
                [scope: "AGENT", action: "save", outcome: "overflow"]) == 1.0d
        counter("jaiclaw.memory.overflows", [scope: "AGENT"]) == 1.0d
    }

    def "writes are tagged separately by scope"() {
        given:
        delegate.saveMemory(_) >> { args -> args[0] }

        when:
        instrumented.saveMemory(MemoryDocument.forTenant("acme", "x", 4096))
        instrumented.saveMemory(MemoryDocument.forAgent("acme", "bot", "x", 2200))
        instrumented.saveMemory(MemoryDocument.forPeer("acme", "bot", "U99", "x", 1375))

        then:
        counter("jaiclaw.memory.writes",
                [scope: "TENANT", action: "save", outcome: "success"]) == 1.0d
        counter("jaiclaw.memory.writes",
                [scope: "AGENT", action: "save", outcome: "success"]) == 1.0d
        counter("jaiclaw.memory.writes",
                [scope: "PEER", action: "save", outcome: "success"]) == 1.0d
    }

    def "delete increments writes{action=delete}"() {
        when:
        instrumented.deleteMemory("acme", MemoryScope.AGENT, "bot", null)

        then:
        1 * delegate.deleteMemory("acme", MemoryScope.AGENT, "bot", null)
        counter("jaiclaw.memory.writes",
                [scope: "AGENT", action: "delete", outcome: "success"]) == 1.0d
    }

    def "reads are NOT metered"() {
        given:
        delegate.findMemory(_, _, _, _) >> Optional.empty()

        when:
        instrumented.findMemory("acme", MemoryScope.AGENT, "bot", null)

        then:
        1 * delegate.findMemory(_, _, _, _) >> Optional.empty()
        registry.find("jaiclaw.memory.reads").counter() == null
    }
}
