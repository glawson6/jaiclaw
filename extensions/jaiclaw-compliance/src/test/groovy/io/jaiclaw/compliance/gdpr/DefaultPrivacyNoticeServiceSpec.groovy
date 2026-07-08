package io.jaiclaw.compliance.gdpr

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.AuditLogger
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DefaultPrivacyNoticeServiceSpec extends Specification {

    Clock fixed = Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneOffset.UTC)

    def "first-time delivery returns notice text and emits displayed event"() {
        given:
        AuditLogger logger = Mock()
        def svc = new DefaultPrivacyNoticeService("Your privacy matters", [logger], fixed)
        AuditEvent captured = null

        when:
        String text = svc.ensureNoticeDelivered("acme", "user-42", "en-US")

        then:
        text == "Your privacy matters"
        1 * logger.log(_ as AuditEvent) >> { AuditEvent evt -> captured = evt }
        captured.action() == DefaultPrivacyNoticeService.ACTION_DISPLAYED
        captured.tenantId() == "acme"
        captured.resource() == "subject:user-42"
    }

    def "subsequent calls return null and do not re-emit"() {
        given:
        AuditLogger logger = Mock()
        def svc = new DefaultPrivacyNoticeService("Your privacy matters", [logger], fixed)

        when:
        String first = svc.ensureNoticeDelivered("acme", "user-42", "en-US")
        String second = svc.ensureNoticeDelivered("acme", "user-42", "en-US")

        then: "first call emitted; second call did not"
        first == "Your privacy matters"
        second == null
        1 * logger.log(_ as AuditEvent)
    }

    def "recordAcceptance emits an accepted event and marks the subject as accepted"() {
        given:
        AuditLogger logger = Mock()
        def svc = new DefaultPrivacyNoticeService("txt", [logger], fixed)
        AuditEvent captured = null

        when:
        svc.recordAcceptance("acme", "user-42")

        then:
        1 * logger.log(_ as AuditEvent) >> { AuditEvent evt -> captured = evt }
        captured.action() == DefaultPrivacyNoticeService.ACTION_ACCEPTED
        svc.hasSeenNotice("acme", "user-42")
    }

    def "different tenants are isolated"() {
        given:
        AuditLogger logger = Mock()
        def svc = new DefaultPrivacyNoticeService("txt", [logger], fixed)

        when:
        String t1 = svc.ensureNoticeDelivered("acme", "user-42", "en-US")
        String t2 = svc.ensureNoticeDelivered("beta", "user-42", "en-US")

        then:
        t1 == "txt"
        t2 == "txt"
        2 * logger.log(_ as AuditEvent)
    }

    def "blank tenant / subject is rejected"() {
        given:
        def svc = new DefaultPrivacyNoticeService("txt", [], fixed)

        when:
        svc.ensureNoticeDelivered(t, s, "en")

        then:
        thrown(IllegalArgumentException)

        where:
        t      | s
        null   | "u"
        ""     | "u"
        "acme" | null
        "acme" | ""
    }
}
