package io.jaiclaw.audit

import io.jaiclaw.core.data.RetentionPolicy
import io.jaiclaw.core.tenant.DefaultTenantContext
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RetentionEnforcementServiceSpec extends Specification {

    @TempDir
    Path storeDir

    // Frozen clock so age math is deterministic.
    Clock clock = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC)

    def "empty policy is a no-op"() {
        given:
        def store = new FileTranscriptStore(storeDir)
        def logger = new InMemoryAuditLogger()
        def service = new RetentionEnforcementService([store], [logger], clock)
        def tenant = new DefaultTenantContext("t-1", "T", [:])

        when:
        def result = service.enforceForTenant(tenant, RetentionPolicy.unlimited())

        then:
        result.transcriptsRemoved() == 0
        result.auditEventsRemoved() == 0
    }

    def "null tenant is a no-op"() {
        given:
        def service = new RetentionEnforcementService([], [], clock)

        when:
        def result = service.enforceForTenant(null, RetentionPolicy.uniform(7))

        then:
        result.transcriptsRemoved() == 0
    }

    def "transcripts past TTL are purged; recent ones survive"() {
        given: "a store with two sessions — one old, one fresh"
        def store = new FileTranscriptStore(storeDir)
        def logger = new InMemoryAuditLogger()
        def service = new RetentionEnforcementService([store], [logger], clock)

        Instant oldStart = clock.instant().minus(Duration.ofDays(30))    // 30d ago
        Instant recentStart = clock.instant().minus(Duration.ofDays(3))  // 3d ago
        store.save(new TranscriptSession("s-old", "acme", "agent", "slack", oldStart, []))
        store.save(new TranscriptSession("s-new", "acme", "agent", "slack", recentStart, []))

        def tenant = new DefaultTenantContext("acme", "Acme", [:])
        // 7-day TTL: old past it, new one well within
        def policy = new RetentionPolicy(Duration.ofDays(7), null, null, RetentionPolicy.Action.DELETE)

        when:
        def result = service.enforceForTenant(tenant, policy)

        then:
        result.transcriptsRemoved() == 1
        store.load("s-old").isEmpty()      // purged
        store.load("s-new").isPresent()    // still there
        def events = logger.query("acme", 10)
        events.any { it.action() == "data.retention_purge" }
    }

    def "cross-tenant isolation: purging acme does not touch beta's data"() {
        given:
        def store = new FileTranscriptStore(storeDir)
        def logger = new InMemoryAuditLogger()
        def service = new RetentionEnforcementService([store], [logger], clock)

        Instant oldStart = clock.instant().minus(Duration.ofDays(30))
        store.save(new TranscriptSession("s-acme-old", "acme", "agent", "slack", oldStart, []))
        store.save(new TranscriptSession("s-beta-old", "beta", "agent", "slack", oldStart, []))

        def acmeTenant = new DefaultTenantContext("acme", "Acme", [:])
        def policy = RetentionPolicy.uniform(7)

        when:
        def result = service.enforceForTenant(acmeTenant, policy)

        then:
        result.transcriptsRemoved() == 1
        store.load("s-acme-old").isEmpty()
        store.load("s-beta-old").isPresent()   // beta's data untouched
    }

    def "PurgeResult.empty() and RetentionPolicy invariants"() {
        given:
        def empty = RetentionEnforcementService.PurgeResult.empty()

        expect:
        empty.transcriptsRemoved() == 0
        empty.auditEventsRemoved() == 0
        empty.elapsed() == Duration.ZERO
        RetentionPolicy.unlimited().onExpiry() == RetentionPolicy.Action.DELETE
        RetentionPolicy.uniform(0).isUnlimited()      // zero days → unlimited
        RetentionPolicy.uniform(-5).isUnlimited()     // negative → unlimited
        !RetentionPolicy.uniform(30).isUnlimited()
    }
}
