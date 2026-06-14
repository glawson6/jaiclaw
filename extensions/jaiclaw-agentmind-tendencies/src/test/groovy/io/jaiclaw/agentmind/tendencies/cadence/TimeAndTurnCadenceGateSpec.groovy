package io.jaiclaw.agentmind.tendencies.cadence

import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Spec uses a mutable wrapper around a system clock so we can advance
 * time deterministically. Spock's Mock can stub Clock.instant().
 */
class TimeAndTurnCadenceGateSpec extends Specification {

    Instant now = Instant.parse("2026-06-14T12:00:00Z")
    Clock clock = Mock() {
        instant() >> { now }
        getZone() >> ZoneOffset.UTC
        withZone(_) >> { args -> Mock(Clock) }
    }

    TimeAndTurnCadenceGate gate = new TimeAndTurnCadenceGate(
            Duration.ofMinutes(15), 5, clock)

    def "first-ever call with enough turns hits the gate"() {
        when:
        boolean ok = gate.shouldRun("acme", "u-1", 5)

        then:
        ok
        gate.stats().hits() == 1L
        gate.stats().misses() == 0L
    }

    def "session below minTurns is rejected — counted as a miss"() {
        when:
        boolean ok = gate.shouldRun("acme", "u-1", 4)

        then:
        !ok
        gate.stats().misses() == 1L
        gate.stats().hits() == 0L
    }

    def "second call within minInterval is rejected"() {
        given:
        gate.shouldRun("acme", "u-1", 5)
        gate.recordRun("acme", "u-1")

        when:
        now = now.plus(Duration.ofMinutes(10))
        boolean ok = gate.shouldRun("acme", "u-1", 5)

        then:
        !ok
        gate.stats().misses() >= 1L
    }

    def "call after minInterval has elapsed passes"() {
        given:
        gate.shouldRun("acme", "u-1", 5)
        gate.recordRun("acme", "u-1")

        when:
        now = now.plus(Duration.ofMinutes(16))
        boolean ok = gate.shouldRun("acme", "u-1", 5)

        then:
        ok
    }

    def "different users do not share gate state within a tenant"() {
        given:
        gate.shouldRun("acme", "u-1", 5)
        gate.recordRun("acme", "u-1")

        when: "u-1 is still in cool-down; u-2 should still pass"
        boolean u1 = gate.shouldRun("acme", "u-1", 5)
        boolean u2 = gate.shouldRun("acme", "u-2", 5)

        then:
        !u1
        u2
    }

    def "different tenants do not share gate state for the same user key"() {
        given:
        gate.shouldRun("tenantA", "shared-u", 5)
        gate.recordRun("tenantA", "shared-u")

        when: "tenantA is in cool-down; tenantB sees a fresh user"
        boolean a = gate.shouldRun("tenantA", "shared-u", 5)
        boolean b = gate.shouldRun("tenantB", "shared-u", 5)

        then:
        !a
        b
    }

    def "rejects invalid construction args"() {
        when:
        new TimeAndTurnCadenceGate(null, 5)

        then:
        thrown(IllegalArgumentException)

        when:
        new TimeAndTurnCadenceGate(Duration.ofMinutes(15), 0)

        then:
        thrown(IllegalArgumentException)
    }
}
