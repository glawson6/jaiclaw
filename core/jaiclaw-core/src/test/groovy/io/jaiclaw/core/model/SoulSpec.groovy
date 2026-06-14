package io.jaiclaw.core.model

import spock.lang.Specification

import java.time.Instant

class SoulSpec extends Specification {

    def "forAgent creates AGENT-scope Soul with version 0"() {
        when:
        Soul s = Soul.forAgent("acme", "support-bot", "# Identity\nHelpful.")

        then:
        s.scope() == SoulScope.AGENT
        s.tenantId() == "acme"
        s.agentId() == "support-bot"
        s.markdown() == "# Identity\nHelpful."
        s.version() == 0L
        s.lastModified() != null
    }

    def "forTenant creates TENANT-scope Soul with null agentId"() {
        when:
        Soul s = Soul.forTenant("acme", "# Style\nConcise.")

        then:
        s.scope() == SoulScope.TENANT
        s.tenantId() == "acme"
        s.agentId() == null
        s.markdown() == "# Style\nConcise."
        s.version() == 0L
    }

    def "withMarkdown produces a copy with bumped version and refreshed timestamp"() {
        given:
        Soul original = new Soul(SoulScope.AGENT, "acme", "bot",
                "v1 markdown", Instant.parse("2026-01-01T00:00:00Z"), 5L)

        when:
        Soul updated = original.withMarkdown("v2 markdown")

        then:
        updated.markdown() == "v2 markdown"
        updated.version() == 6L
        updated.scope() == SoulScope.AGENT
        updated.tenantId() == "acme"
        updated.agentId() == "bot"
        updated.lastModified() > original.lastModified()
    }

    def "TENANT scope with non-null agentId is rejected"() {
        when:
        new Soul(SoulScope.TENANT, "acme", "bot", "x", Instant.now(), 0L)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("TENANT")
    }

    def "AGENT scope with null agentId is rejected"() {
        when:
        new Soul(SoulScope.AGENT, "acme", null, "x", Instant.now(), 0L)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("agentId")
    }

    def "AGENT scope with blank agentId is rejected"() {
        when:
        new Soul(SoulScope.AGENT, "acme", "  ", "x", Instant.now(), 0L)

        then:
        thrown(IllegalArgumentException)
    }

    def "blank tenantId is rejected regardless of scope"() {
        when:
        new Soul(scope, "", agentId, "x", Instant.now(), 0L)

        then:
        thrown(IllegalArgumentException)

        where:
        scope            | agentId
        SoulScope.TENANT | null
        SoulScope.AGENT  | "bot"
    }

    def "null markdown is normalized to empty string"() {
        when:
        Soul s = new Soul(SoulScope.TENANT, "acme", null, null, Instant.now(), 0L)

        then:
        s.markdown() == ""
    }
}
