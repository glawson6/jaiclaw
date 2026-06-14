package io.jaiclaw.core.model

import spock.lang.Specification

import java.time.Instant

class TendenciesSpec extends Specification {

    def "forUser creates a USER-scope record with version 0"() {
        when:
        Tendencies t = Tendencies.forUser("acme", "u-uuid",
                "# Profile\nPrefers brevity.", [prefers_brevity: "true"])

        then:
        t.scope() == TendenciesScope.USER
        t.tenantId() == "acme"
        t.canonicalUserId() == "u-uuid"
        t.peerCardMarkdown().contains("brevity")
        t.traits() == [prefers_brevity: "true"]
        t.version() == 0L
        t.dialecticPasses() == 0L
        t.lastDialecticAt() == null
    }

    def "forTenant creates a TENANT-scope record with null canonicalUserId"() {
        when:
        Tendencies t = Tendencies.forTenant("acme",
                "# Org\nBullet points.", [comm_style: "bullets"])

        then:
        t.scope() == TendenciesScope.TENANT
        t.canonicalUserId() == null
        t.traits()["comm_style"] == "bullets"
    }

    def "withDialecticResult bumps version + passes and stamps lastDialecticAt"() {
        given:
        Tendencies original = new Tendencies(TendenciesScope.USER, "acme", "u1",
                "v1", [a: "1"], Instant.parse("2026-01-01T00:00:00Z"), null, 0L, 3L)

        when:
        Tendencies updated = original.withDialecticResult("v2", [a: "1", b: "2"])

        then:
        updated.peerCardMarkdown() == "v2"
        updated.traits() == [a: "1", b: "2"]
        updated.version() == 4L
        updated.dialecticPasses() == 1L
        updated.lastDialecticAt() != null
        updated.updatedAt() > original.updatedAt()
    }

    def "TENANT scope rejects a non-null canonicalUserId"() {
        when:
        new Tendencies(TendenciesScope.TENANT, "acme", "u1", "x", [:], Instant.now(), null, 0L, 0L)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("TENANT")
    }

    def "USER scope rejects a null canonicalUserId"() {
        when:
        new Tendencies(TendenciesScope.USER, "acme", null, "x", [:], Instant.now(), null, 0L, 0L)

        then:
        thrown(IllegalArgumentException)
    }

    def "USER scope rejects a blank canonicalUserId"() {
        when:
        new Tendencies(TendenciesScope.USER, "acme", "  ", "x", [:], Instant.now(), null, 0L, 0L)

        then:
        thrown(IllegalArgumentException)
    }

    def "blank tenantId is always rejected"() {
        when:
        new Tendencies(scope, "", uid, "x", [:], Instant.now(), null, 0L, 0L)

        then:
        thrown(IllegalArgumentException)

        where:
        scope                  | uid
        TendenciesScope.TENANT | null
        TendenciesScope.USER   | "u1"
    }

    def "null markdown is normalized to empty string"() {
        when:
        Tendencies t = new Tendencies(TendenciesScope.TENANT, "acme", null,
                null, [:], Instant.now(), null, 0L, 0L)

        then:
        t.peerCardMarkdown() == ""
    }

    def "null traits is normalized to empty map"() {
        when:
        Tendencies t = Tendencies.forTenant("acme", "x", null)

        then:
        t.traits() == [:]
    }

    def "traits map is defensively copied"() {
        given:
        Map<String, String> mutable = [a: "1"]
        Tendencies t = Tendencies.forUser("acme", "u", "x", mutable)

        when:
        mutable["b"] = "2"

        then:
        t.traits() == [a: "1"]
    }
}
