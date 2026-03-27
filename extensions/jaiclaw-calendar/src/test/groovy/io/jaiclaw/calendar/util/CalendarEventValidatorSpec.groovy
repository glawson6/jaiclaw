package io.jaiclaw.calendar.util

import io.jaiclaw.calendar.config.CalendarProperties
import spock.lang.Specification

import java.time.Instant

class CalendarEventValidatorSpec extends Specification {

    CalendarEventValidator validator

    def setup() {
        validator = new CalendarEventValidator(new CalendarProperties())
    }

    def "valid future event passes"() {
        given:
        def start = Instant.now().plusSeconds(3600)
        def end = start.plusSeconds(3600)

        when:
        def result = validator.validateEventTiming(start, end)

        then:
        result.isEmpty()
    }

    def "past start time fails"() {
        given:
        def start = Instant.now().minusSeconds(3600)
        def end = Instant.now().plusSeconds(3600)

        when:
        def result = validator.validateEventTiming(start, end)

        then:
        result.isPresent()
        result.get().contains("past")
    }

    def "end before start fails"() {
        given:
        def start = Instant.now().plusSeconds(7200)
        def end = Instant.now().plusSeconds(3600)

        when:
        def result = validator.validateEventTiming(start, end)

        then:
        result.isPresent()
        result.get().contains("after start")
    }

    def "too short duration fails"() {
        given:
        def start = Instant.now().plusSeconds(3600)
        def end = start.plusSeconds(60) // 1 minute, below 30 min minimum

        when:
        def result = validator.validateEventTiming(start, end)

        then:
        result.isPresent()
        result.get().contains("at least")
    }

    def "validateTimeRange skips past check"() {
        given:
        def start = Instant.now().minusSeconds(7200)
        def end = start.plusSeconds(3600) // 1 hour duration, valid

        when:
        def result = validator.validateTimeRange(start, end)

        then:
        result.isEmpty()
    }

    def "validateTimeRange rejects end before start"() {
        given:
        def start = Instant.now().plusSeconds(7200)
        def end = Instant.now().plusSeconds(3600)

        when:
        def result = validator.validateTimeRange(start, end)

        then:
        result.isPresent()
    }
}
