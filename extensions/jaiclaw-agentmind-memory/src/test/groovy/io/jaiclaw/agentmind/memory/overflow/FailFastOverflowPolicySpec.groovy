package io.jaiclaw.agentmind.memory.overflow

import io.jaiclaw.core.agent.MemoryOverflowException
import io.jaiclaw.core.model.MemoryDocument
import io.jaiclaw.core.model.MemoryScope
import spock.lang.Specification

class FailFastOverflowPolicySpec extends Specification {

    FailFastOverflowPolicy policy = new FailFastOverflowPolicy()

    def "raises MemoryOverflowException carrying scope, budget, and attempted length"() {
        given:
        MemoryDocument doc = MemoryDocument.forAgent("acme", "bot", "x" * 200, 100)

        when:
        policy.resolve(doc)

        then:
        MemoryOverflowException e = thrown()
        e.scope() == MemoryScope.AGENT
        e.charBudget() == 100
        e.attemptedLength() == 200
    }

    def "raises for all three scopes"() {
        given:
        MemoryDocument doc = makeDoc.call()

        when:
        policy.resolve(doc)

        then:
        thrown(MemoryOverflowException)

        where:
        makeDoc << [
                { MemoryDocument.forTenant("acme", "x" * 100, 50) },
                { MemoryDocument.forAgent("acme", "bot", "x" * 100, 50) },
                { MemoryDocument.forPeer("acme", "bot", "user", "x" * 100, 50) }
        ]
    }
}
