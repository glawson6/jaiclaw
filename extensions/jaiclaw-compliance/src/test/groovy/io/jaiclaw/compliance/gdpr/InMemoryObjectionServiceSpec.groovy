package io.jaiclaw.compliance.gdpr

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.AuditLogger
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class InMemoryObjectionServiceSpec extends Specification {

    Clock fixed = Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneOffset.UTC)

    def "recordObjection emits recorded event and shows in getObjections"() {
        given:
        AuditLogger logger = Mock()
        def svc = new InMemoryObjectionService([logger], fixed)
        AuditEvent captured = null

        when:
        def token = svc.recordObjection("acme", "user-42", "profiling", "click-log:abc")

        then:
        1 * logger.log(_ as AuditEvent) >> { AuditEvent e -> captured = e }
        captured.action() == InMemoryObjectionService.ACTION_RECORDED
        captured.details()["processingPurpose"] == "profiling"
        token.processingPurpose() == "profiling"
        svc.hasObjection("acme", "user-42", "profiling")
        svc.getObjections("acme", "user-42").keySet() == ["profiling"] as Set
    }

    def "rescindObjection removes state and emits rescinded event"() {
        given:
        AuditLogger logger = Mock()
        def svc = new InMemoryObjectionService([logger], fixed)
        AuditEvent last = null

        when:
        svc.recordObjection("acme", "user-42", "profiling", "p")
        svc.rescindObjection("acme", "user-42", "profiling")

        then:
        2 * logger.log(_ as AuditEvent) >> { AuditEvent e -> last = e }
        last.action() == InMemoryObjectionService.ACTION_RESCINDED
        !svc.hasObjection("acme", "user-42", "profiling")
    }

    def "objections are isolated per tenant"() {
        given:
        AuditLogger logger = Mock()
        def svc = new InMemoryObjectionService([logger], fixed)

        when:
        svc.recordObjection("acme", "user-42", "marketing", "p1")
        svc.recordObjection("beta", "user-42", "marketing", "p2")

        then:
        2 * logger.log(_ as AuditEvent)
        svc.hasObjection("acme", "user-42", "marketing")
        svc.hasObjection("beta", "user-42", "marketing")
        !svc.hasObjection("acme", "user-99", "marketing")
    }

    def "blank input is rejected"() {
        given:
        def svc = new InMemoryObjectionService([], fixed)

        when:
        svc.recordObjection(t, s, p, "proof")

        then:
        thrown(IllegalArgumentException)

        where:
        t      | s     | p
        null   | "u"   | "p"
        ""     | "u"   | "p"
        "acme" | null  | "p"
        "acme" | ""    | "p"
        "acme" | "u"   | null
        "acme" | "u"   | ""
    }
}
