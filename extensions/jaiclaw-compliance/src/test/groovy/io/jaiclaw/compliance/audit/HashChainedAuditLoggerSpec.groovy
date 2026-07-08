package io.jaiclaw.compliance.audit

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.AuditLogger
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

class HashChainedAuditLoggerSpec extends Specification {

    Clock fixed = Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneOffset.UTC)

    /** Simple in-memory delegate for testing chain behavior. */
    private static class InMemoryLogger implements AuditLogger {
        List<AuditEvent> events = []
        @Override void log(AuditEvent e) { events.add(e) }
        @Override List<AuditEvent> query(String t, int l) { events.reverse() }
        @Override Optional<AuditEvent> findById(String id) { events.find { it.id() == id } ? Optional.of(events.find { it.id() == id }) : Optional.empty() }
        @Override long count(String t) { events.size() }
    }

    def "each event carries a prevHash + chainHash"() {
        given:
        InMemoryLogger inner = new InMemoryLogger()
        def logger = new HashChainedAuditLogger(inner, fixed)

        when:
        logger.log(event("evt-1"))
        logger.log(event("evt-2"))
        logger.log(event("evt-3"))

        then:
        inner.events.size() == 3
        inner.events.each { it.details()["prevHash"] != null && it.details()["chainHash"] != null }
        inner.events[0].details()["prevHash"] == "GENESIS"
        inner.events[1].details()["prevHash"] == inner.events[0].details()["chainHash"]
        inner.events[2].details()["prevHash"] == inner.events[1].details()["chainHash"]
    }

    def "verifyChain returns valid=true for an untampered chain"() {
        given:
        InMemoryLogger inner = new InMemoryLogger()
        def logger = new HashChainedAuditLogger(inner, fixed)
        (1..5).each { logger.log(event("evt-${it}")) }

        when:
        def report = logger.verifyChain("acme")

        then:
        report.valid()
        report.brokenAt() == -1
    }

    def "verifyChain reports the break when an event is mutated"() {
        given:
        InMemoryLogger inner = new InMemoryLogger()
        def logger = new HashChainedAuditLogger(inner, fixed)
        (1..5).each { logger.log(event("evt-${it}")) }

        when: "manually mutate the middle event's details"
        AuditEvent mid = inner.events[2]
        Map mutated = new HashMap(mid.details())
        mutated.put("tampered", "yes")
        inner.events[2] = AuditEvent.builder()
                .id(mid.id()).timestamp(mid.timestamp()).tenantId(mid.tenantId())
                .actor(mid.actor()).action(mid.action()).resource(mid.resource())
                .outcome(mid.outcome()).details(mutated)
                .build()
        def report = logger.verifyChain("acme")

        then:
        !report.valid()
        report.brokenAt() == 2
        report.offendingEventId() == "evt-3"
    }

    def "verifyChain emits an audit.integrity_violation event on a break"() {
        given:
        InMemoryLogger inner = new InMemoryLogger()
        def logger = new HashChainedAuditLogger(inner, fixed)
        (1..3).each { logger.log(event("evt-${it}")) }
        // Tamper.
        AuditEvent mid = inner.events[1]
        inner.events[1] = AuditEvent.builder()
                .id(mid.id()).timestamp(mid.timestamp()).tenantId(mid.tenantId())
                .actor(mid.actor()).action("mutated").resource(mid.resource())
                .outcome(mid.outcome()).details(mid.details()).build()

        when:
        logger.verifyChain("acme")

        then:
        inner.events.find { it.action() == HashChainedAuditLogger.ACTION_INTEGRITY_VIOLATION } != null
    }

    private AuditEvent event(String id) {
        AuditEvent.builder()
                .id(id).timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .tenantId("acme").actor("system").action("test.event").resource("x")
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details([:])
                .build()
    }
}
