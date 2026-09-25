package io.jaiclaw.compliance.audit

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.AuditLogger
import io.jaiclaw.compliance.encryption.AesGcmFieldEncryptor
import io.jaiclaw.compliance.encryption.EncryptedAuditLogger
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The control an auditor actually tests: can this deployment demonstrate that
 * its audit log was not altered?
 *
 * <p>SOC 2 CC7.2 is not satisfied by "we have logs" — it is satisfied by being
 * able to show that a modification would be detected. These specs exercise the
 * full round trip: write events, verify clean, tamper, verify again and confirm
 * the break is both detected and *located*.
 *
 * <p>The existing {@code HashChainedAuditLoggerSpec} covers chain mechanics.
 * This one covers the operator-facing claim that depends on them.
 */
class AuditChainTamperSpec extends Specification {

    static final String TENANT = "acme"

    Clock fixed = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC)

    /**
     * Mutable in-memory delegate. Events are stored in a list we can reach into,
     * which is what makes tampering simulable — a real attacker would be editing
     * the JSONL on disk.
     */
    static class MutableLogger implements AuditLogger {
        List<AuditEvent> events = []
        @Override void log(AuditEvent e) { events.add(e) }
        @Override List<AuditEvent> query(String t, int l) { events.reverse() }   // most-recent-first, per SPI
        @Override Optional<AuditEvent> findById(String id) {
            Optional.ofNullable(events.find { it.id() == id })
        }
        @Override long count(String t) { events.size() }
    }

    def "an untampered chain verifies clean"() {
        given:
        def inner = new MutableLogger()
        def logger = new HashChainedAuditLogger(inner, fixed)

        when: "a run of ordinary audit events"
        (1..5).each { logger.log(event("evt-$it", "tool.invoke")) }

        then:
        def report = logger.verifyChain(TENANT)
        report.valid()
        report.brokenAt() == -1
        report.offendingEventId() == null
    }

    def "altering an event's ACTION is detected, and the offending event is named"() {
        given:
        def inner = new MutableLogger()
        def logger = new HashChainedAuditLogger(inner, fixed)
        (1..5).each { logger.log(event("evt-$it", "tool.invoke")) }
        assert logger.verifyChain(TENANT).valid()

        when: "someone rewrites the third event to hide what was actually run"
        inner.events[2] = rewriteAction(inner.events[2], "benign.read")

        then: "the chain hash no longer recomputes"
        def report = logger.verifyChain(TENANT)
        !report.valid()
        report.reason() == "chainHash recomputation mismatch"

        and: "and it names WHICH event — the difference between 'something changed' and evidence"
        report.brokenAt() == 2
        report.offendingEventId() == "evt-3"
    }

    def "altering the ACTOR is detected — the most likely thing to falsify"() {
        given:
        def inner = new MutableLogger()
        def logger = new HashChainedAuditLogger(inner, fixed)
        (1..3).each { logger.log(event("evt-$it", "tool.invoke")) }

        when: "attributing an action to someone else"
        inner.events[1] = rewriteActor(inner.events[1], "someone-else")

        then:
        def report = logger.verifyChain(TENANT)
        !report.valid()
        report.brokenAt() == 1
    }

    def "DELETING an event is detected — the chain is broken by the gap"() {
        given:
        def inner = new MutableLogger()
        def logger = new HashChainedAuditLogger(inner, fixed)
        (1..5).each { logger.log(event("evt-$it", "tool.invoke")) }

        when: "excising the record of an action entirely"
        inner.events.remove(2)

        then: "the following event's prevHash no longer matches its predecessor"
        def report = logger.verifyChain(TENANT)
        !report.valid()
        report.reason() == "prevHash mismatch"
    }

    def "TRUNCATING the tail is NOT detected — a documented limitation"() {
        given:
        def inner = new MutableLogger()
        def logger = new HashChainedAuditLogger(inner, fixed)
        (1..5).each { logger.log(event("evt-$it", "tool.invoke")) }

        when: "dropping the most recent events, leaving a shorter valid prefix"
        inner.events = inner.events[0..2]

        then: "a hash chain cannot detect this — the remaining prefix is internally consistent"
        logger.verifyChain(TENANT).valid()

        and: """this is inherent to chaining, not a defect: detecting truncation needs an
                external anchor (a counter-signed head, or shipping events off-host).
                Worth stating plainly so nobody claims a control that does not exist."""
        true
    }

    def "stripping the chain fields is detected"() {
        given:
        def inner = new MutableLogger()
        def logger = new HashChainedAuditLogger(inner, fixed)
        (1..3).each { logger.log(event("evt-$it", "tool.invoke")) }

        when: "removing the evidence of chaining rather than editing it"
        inner.events[1] = stripChainFields(inner.events[1])

        then:
        def report = logger.verifyChain(TENANT)
        !report.valid()
        report.reason() == "missing chain fields"
    }

    def "a detected break emits an audit.integrity_violation event"() {
        given:
        def inner = new MutableLogger()
        def logger = new HashChainedAuditLogger(inner, fixed)
        (1..3).each { logger.log(event("evt-$it", "tool.invoke")) }
        inner.events[1] = rewriteAction(inner.events[1], "tampered")

        when:
        logger.verifyChain(TENANT)

        then: "the violation is itself auditable — this is what a SIEM alerts on"
        inner.events.any { it.action() == HashChainedAuditLogger.ACTION_INTEGRITY_VIOLATION }
    }

    def "the chain survives being stacked OUTSIDE the encryptor"() {
        given: """the soc2 nesting: HashChained(Encrypted(inner)). The chain hashes
                  plaintext and the encryptor protects the payload beneath it."""
        def inner = new MutableLogger()
        def encryptor = new AesGcmFieldEncryptor(AesGcmFieldEncryptor.generateKey())
        def logger = new HashChainedAuditLogger(new EncryptedAuditLogger(inner, encryptor), fixed)

        when:
        (1..4).each { logger.log(event("evt-$it", "tool.invoke")) }

        then: "verification still works through the encrypting layer"
        logger.verifyChain(TENANT).valid()
    }

    // ---- helpers ----

    private AuditEvent event(String id, String action) {
        AuditEvent.builder()
                .id(id)
                .timestamp(fixed.instant())
                .tenantId(TENANT)
                .actor("alice")
                .action(action)
                .resource("shell_exec")
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details([:])
                .build()
    }

    private static AuditEvent rewriteAction(AuditEvent e, String action) {
        copyWith(e) { b -> b.action(action) }
    }

    private static AuditEvent rewriteActor(AuditEvent e, String actor) {
        copyWith(e) { b -> b.actor(actor) }
    }

    /** Keeps every field EXCEPT the chain stamps — simulating their removal. */
    private static AuditEvent stripChainFields(AuditEvent e) {
        def details = new HashMap<>(e.details())
        details.remove("prevHash")
        details.remove("chainHash")
        copyWith(e) { b -> b.details(details) }
    }

    private static AuditEvent copyWith(AuditEvent e, Closure mutate) {
        def b = AuditEvent.builder()
                .id(e.id()).timestamp(e.timestamp()).tenantId(e.tenantId())
                .actor(e.actor()).action(e.action()).resource(e.resource())
                .outcome(e.outcome()).details(e.details())
                .lawfulBasis(e.lawfulBasis()).dataCategories(e.dataCategories())
                .recipients(e.recipients()).retentionDays(e.retentionDays())
                .consentToken(e.consentToken())
        mutate(b)
        b.build()
    }
}
