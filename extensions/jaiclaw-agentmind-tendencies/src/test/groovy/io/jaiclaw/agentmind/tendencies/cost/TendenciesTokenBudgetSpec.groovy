package io.jaiclaw.agentmind.tendencies.cost

import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TendenciesTokenBudgetSpec extends Specification {

    Instant now = Instant.parse("2026-06-14T12:00:00Z")
    Clock clock = Mock() {
        instant() >> { now }
        getZone() >> ZoneOffset.UTC
        withZone(_) >> { args -> Mock(Clock) {
            instant() >> { now }
            getZone() >> args[0]
        } }
    }

    TendenciesTokenBudget budget = new TendenciesTokenBudget(1000L, clock)

    def "canSpend returns true when below cap"() {
        expect:
        budget.canSpend("acme", 500L)
    }

    def "canSpend returns false when adding the request would exceed cap"() {
        given:
        budget.recordSpend("acme", 800L)

        expect:
        budget.canSpend("acme", 300L) == false
    }

    def "recordSpend accumulates within the day"() {
        when:
        budget.recordSpend("acme", 200L)
        budget.recordSpend("acme", 300L)

        then:
        budget.snapshot("acme").spent() == 500L
        budget.snapshot("acme").remaining() == 500L
    }

    def "different tenants have independent budgets"() {
        when:
        budget.recordSpend("tenantA", 900L)

        then:
        !budget.canSpend("tenantA", 200L)
        budget.canSpend("tenantB", 999L) // tenantB is fresh
    }

    def "counter resets at UTC day boundary"() {
        given:
        budget.recordSpend("acme", 900L)

        when:
        // Advance one day in UTC
        now = now.plus(java.time.Duration.ofDays(1))

        then:
        budget.canSpend("acme", 999L)
        budget.snapshot("acme").spent() == 0L
    }

    def "snapshot for unknown tenant returns zero spent"() {
        expect:
        budget.snapshot("ghost").spent() == 0L
        budget.snapshot("ghost").remaining() == 1000L
        budget.snapshot("ghost").dailyCap() == 1000L
    }

    def "rejects invalid dailyCap at construction"() {
        when:
        new TendenciesTokenBudget(0L)

        then:
        thrown(IllegalArgumentException)
    }
}
