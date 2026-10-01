package io.jaiclaw.compliance.audit

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.AuditLogger
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * The scheduled half of tamper-evident audit.
 *
 * <p>{@code verifyChain} had zero callers before 1.3.0 — the chain was
 * maintained on every write but nothing ever read it, so a break would surface
 * only if an operator ran the check by hand.
 */
class AuditChainVerifierSpec extends Specification {

    @TempDir
    Path tmp

    /** Same fake the HashChainedAuditLogger specs use. */
    private static class InMemoryLogger implements AuditLogger {
        List<AuditEvent> events = []
        @Override void log(AuditEvent e) { events.add(e) }
        @Override List<AuditEvent> query(String t, int l) { events.reverse() }
        @Override Optional<AuditEvent> findById(String id) {
            Optional.ofNullable(events.find { it.id() == id })
        }
        @Override long count(String t) { events.size() }
    }

    private HashChainedAuditLogger chained(AuditLogger delegate = new InMemoryLogger()) {
        new HashChainedAuditLogger(delegate)
    }

    private static int seq = 0

    /** id and timestamp are required by AuditEvent.builder(). */
    private static AuditEvent event(String tenantId, String action) {
        AuditEvent.builder()
                .id("evt-" + (++seq))
                .timestamp(Instant.parse("2026-07-07T12:00:00Z"))
                .tenantId(tenantId)
                .action(action)
                .actor("tester")
                .build()
    }

    def "a clean chain produces no failures"() {
        given:
        def logger = chained()
        3.times { logger.log(event("_default", "thing.$it")) }
        def verifier = new AuditChainVerifier([logger], tmp)

        when:
        def failures = verifier.verifyOnce()

        then:
        failures.isEmpty()
        verifier.failureCount() == 0
        verifier.lastRunAt() != null
    }

    def "a broken chain is reported"() {
        given: "a chain whose middle record has been altered"
        def inner = new InMemoryLogger()
        def logger = chained(inner)
        3.times { logger.log(event("_default", "thing.$it")) }

        and: "replace a stored record so the replay cannot reproduce its hash"
        AuditEvent mid = inner.events[1]
        Map mutated = new HashMap(mid.details())
        mutated.put("tampered", "yes")
        inner.events[1] = AuditEvent.builder()
                .id(mid.id()).timestamp(mid.timestamp()).tenantId(mid.tenantId())
                .actor(mid.actor()).action(mid.action()).resource(mid.resource())
                .outcome(mid.outcome()).details(mutated)
                .build()

        def verifier = new AuditChainVerifier([logger], tmp)

        when:
        def failures = verifier.verifyOnce()

        then:
        !failures.isEmpty()
        !failures[0].valid()
        verifier.failureCount() >= 1
    }

    def "tenants are discovered from the audit store directory layout"() {
        given: "FileAuditLogger writes {storeDir}/{tenantId}/"
        Files.createDirectories(tmp.resolve("acme"))
        Files.createDirectories(tmp.resolve("beta"))
        Files.createDirectories(tmp.resolve("_default"))
        def verifier = new AuditChainVerifier([chained()], tmp)

        when:
        def tenants = verifier.discoverTenants()

        then: "no tenant registry exists, so the store on disk is the roster"
        tenants == ["acme", "beta", "_default"] as Set
    }

    def "an empty or missing store still checks the default tenant"() {
        given: "otherwise a green result would mean 'checked nothing'"
        def verifier = new AuditChainVerifier([chained()], tmp.resolve("does-not-exist"))

        expect:
        verifier.discoverTenants() == ["_default"] as Set
    }

    def "a null store directory does not break discovery"() {
        given: "in-memory audit deployments have no directory at all"
        def verifier = new AuditChainVerifier([chained()], null)

        expect:
        verifier.discoverTenants() == ["_default"] as Set
    }

    def "every discovered tenant is checked"() {
        given:
        Files.createDirectories(tmp.resolve("acme"))
        Files.createDirectories(tmp.resolve("beta"))
        def logger = chained()
        logger.log(event("acme", "a"))
        logger.log(event("beta", "b"))
        def verifier = new AuditChainVerifier([logger], tmp)

        when:
        verifier.verifyOnce()

        then:
        verifier.lastTenantsChecked() == 2
    }

    def "a non-chained AuditLogger is ignored rather than failing"() {
        given: "the decorator is opt-in; a plain logger has no chain to verify"
        def verifier = new AuditChainVerifier([new InMemoryLogger()], tmp)

        when:
        def failures = verifier.verifyOnce()

        then:
        failures.isEmpty()
        verifier.lastRunAt() == null
    }

    def "start is a no-op when no chained logger is present"() {
        given: "nothing to verify means no thread to run"
        def verifier = new AuditChainVerifier([new InMemoryLogger()], tmp)

        when:
        verifier.start()

        then:
        !verifier.isRunning()

        cleanup:
        verifier.close()
    }

    def "start launches a worker and close stops it"() {
        given:
        def verifier = new AuditChainVerifier([chained()], tmp,
                Duration.ofHours(1), null)

        when:
        verifier.start()

        then:
        verifier.isRunning()

        when:
        verifier.close()

        then:
        !verifier.isRunning()
    }

    def "start is idempotent"() {
        given:
        def verifier = new AuditChainVerifier([chained()], tmp, Duration.ofHours(1), null)

        when:
        verifier.start()
        verifier.start()

        then:
        noExceptionThrown()
        verifier.isRunning()

        cleanup:
        verifier.close()
    }

    def "the first pass is deferred — startup does no verification work"() {
        given: "verification reads whole tenant histories"
        def logger = chained()
        logger.log(event("_default", "a"))
        def verifier = new AuditChainVerifier([logger], tmp, Duration.ofHours(1), null)

        when:
        verifier.start()
        Thread.sleep(50)

        then: "nothing has run yet"
        verifier.lastRunAt() == null

        cleanup:
        verifier.close()
    }

    def "a non-positive interval falls back to the default"() {
        expect: "a zero interval would spin"
        new AuditChainVerifier([chained()], tmp, interval, null)
                .with { it.close(); true }

        where:
        interval << [Duration.ZERO, Duration.ofSeconds(-5), null]
    }

    def "multiple chained loggers are each verified"() {
        given: "each AuditLogger bean gets its own decorator with its own chain state"
        def a = chained()
        def b = chained()
        a.log(event("_default", "a"))
        b.log(event("_default", "b"))
        def verifier = new AuditChainVerifier([a, b], tmp)

        when:
        def failures = verifier.verifyOnce()

        then:
        failures.isEmpty()
        verifier.lastRunAt() != null
    }
}
