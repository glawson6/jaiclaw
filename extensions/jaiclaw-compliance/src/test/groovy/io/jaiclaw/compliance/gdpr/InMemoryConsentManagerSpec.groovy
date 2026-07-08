package io.jaiclaw.compliance.gdpr

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.AuditLogger
import io.jaiclaw.core.gdpr.ConsentManager
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class InMemoryConsentManagerSpec extends Specification {

    Clock fixed = Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneOffset.UTC)

    def "recordConsent stores + emits recorded audit event"() {
        given:
        AuditLogger logger = Mock()
        def mgr = new InMemoryConsentManager([logger], fixed)
        AuditEvent captured = null

        when:
        ConsentManager.ConsentToken token = mgr.recordConsent("acme", "user-42", "processing", "signed:abc")

        then:
        1 * logger.log(_ as AuditEvent) >> { AuditEvent evt -> captured = evt }
        token.token() != null
        token.tenantId() == "acme"
        token.dataSubjectId() == "user-42"
        token.consentType() == "processing"
        captured.action() == InMemoryConsentManager.ACTION_RECORDED
        captured.tenantId() == "acme"
        captured.details()["consentType"] == "processing"

        and: "getConsentStatus reports the consent"
        mgr.getConsentStatus("acme", "user-42") == ["processing": Instant.parse("2026-07-07T12:00:00Z")]
    }

    def "withdrawConsent removes state + emits withdrawn audit event"() {
        given:
        AuditLogger logger = Mock()
        def mgr = new InMemoryConsentManager([logger], fixed)
        AuditEvent lastEvent = null

        when:
        mgr.recordConsent("acme", "user-42", "processing", "signed:abc")
        mgr.withdrawConsent("acme", "user-42", "processing")

        then: "two audit events: one recorded, one withdrawn"
        2 * logger.log(_ as AuditEvent) >> { AuditEvent evt -> lastEvent = evt }
        lastEvent.action() == InMemoryConsentManager.ACTION_WITHDRAWN
        mgr.getConsentStatus("acme", "user-42").isEmpty()
    }

    def "withdrawing a consent that was never recorded still emits an event"() {
        given:
        AuditLogger logger = Mock()
        def mgr = new InMemoryConsentManager([logger], fixed)

        when:
        mgr.withdrawConsent("acme", "user-42", "marketing")

        then:
        1 * logger.log(_ as AuditEvent)
    }

    def "records for different tenants are isolated"() {
        given:
        AuditLogger logger = Mock()
        def mgr = new InMemoryConsentManager([logger], fixed)

        when:
        mgr.recordConsent("acme", "user-42", "processing", "p1")
        mgr.recordConsent("beta", "user-42", "processing", "p2")

        then:
        2 * logger.log(_ as AuditEvent)
        mgr.getConsentStatus("acme", "user-42").keySet() == ["processing"] as Set
        mgr.getConsentStatus("beta", "user-42").keySet() == ["processing"] as Set
        mgr.getConsentStatus("acme", "user-99").isEmpty()
    }

    def "blank tenant / subject / type is rejected"() {
        given:
        def mgr = new InMemoryConsentManager([], fixed)

        when:
        mgr.recordConsent(t, s, c, "proof")

        then:
        thrown(IllegalArgumentException)

        where:
        t      | s     | c
        null   | "u"   | "c"
        ""     | "u"   | "c"
        "acme" | null  | "c"
        "acme" | ""    | "c"
        "acme" | "u"   | null
        "acme" | "u"   | ""
    }
}
