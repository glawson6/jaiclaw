package io.jaiclaw.core.model

import spock.lang.Specification

import java.time.Instant

class MemoryDocumentSpec extends Specification {

    def "forTenant creates a TENANT-scope record with version 0"() {
        when:
        MemoryDocument m = MemoryDocument.forTenant("acme", "# Outages\nUse Slack #incidents.", 4096)

        then:
        m.scope() == MemoryScope.TENANT
        m.tenantId() == "acme"
        m.agentId() == null
        m.peerId() == null
        m.content().contains("Outages")
        m.charBudget() == 4096
        m.version() == 0L
    }

    def "forAgent creates an AGENT-scope record"() {
        when:
        MemoryDocument m = MemoryDocument.forAgent("acme", "bot", "agent memory", 2200)

        then:
        m.scope() == MemoryScope.AGENT
        m.agentId() == "bot"
        m.peerId() == null
    }

    def "forPeer creates a PEER-scope record"() {
        when:
        MemoryDocument m = MemoryDocument.forPeer("acme", "bot", "user-42", "peer memory", 1375)

        then:
        m.scope() == MemoryScope.PEER
        m.agentId() == "bot"
        m.peerId() == "user-42"
    }

    def "withContent produces a copy with bumped version"() {
        given:
        MemoryDocument original = new MemoryDocument(MemoryScope.AGENT, "acme", "bot", null,
                "v1", 2200, Instant.parse("2026-01-01T00:00:00Z"), 5L)

        when:
        MemoryDocument updated = original.withContent("v2")

        then:
        updated.content() == "v2"
        updated.version() == 6L
        updated.charBudget() == 2200
        updated.updatedAt() > original.updatedAt()
    }

    // ---------- nullability invariants ----------

    def "TENANT scope rejects a non-null agentId"() {
        when:
        new MemoryDocument(MemoryScope.TENANT, "acme", "bot", null, "x", 4096, Instant.now(), 0L)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("TENANT")
    }

    def "TENANT scope rejects a non-null peerId"() {
        when:
        new MemoryDocument(MemoryScope.TENANT, "acme", null, "peer", "x", 4096, Instant.now(), 0L)

        then:
        thrown(IllegalArgumentException)
    }

    def "AGENT scope rejects a null agentId"() {
        when:
        new MemoryDocument(MemoryScope.AGENT, "acme", null, null, "x", 2200, Instant.now(), 0L)

        then:
        thrown(IllegalArgumentException)
    }

    def "AGENT scope rejects a non-null peerId"() {
        when:
        new MemoryDocument(MemoryScope.AGENT, "acme", "bot", "peer", "x", 2200, Instant.now(), 0L)

        then:
        thrown(IllegalArgumentException)
    }

    def "PEER scope rejects a null agentId"() {
        when:
        new MemoryDocument(MemoryScope.PEER, "acme", null, "peer", "x", 1375, Instant.now(), 0L)

        then:
        thrown(IllegalArgumentException)
    }

    def "PEER scope rejects a null peerId"() {
        when:
        new MemoryDocument(MemoryScope.PEER, "acme", "bot", null, "x", 1375, Instant.now(), 0L)

        then:
        thrown(IllegalArgumentException)
    }

    def "blank tenantId is always rejected"() {
        when:
        new MemoryDocument(MemoryScope.AGENT, "  ", "bot", null, "x", 1000, Instant.now(), 0L)

        then:
        thrown(IllegalArgumentException)
    }

    def "non-positive charBudget is rejected"() {
        when:
        new MemoryDocument(MemoryScope.AGENT, "acme", "bot", null, "x", budget, Instant.now(), 0L)

        then:
        thrown(IllegalArgumentException)

        where:
        budget << [0, -1, -100]
    }

    def "null content is normalized to empty string"() {
        when:
        MemoryDocument m = MemoryDocument.forAgent("acme", "bot", null, 2200)

        then:
        m.content() == ""
    }
}
