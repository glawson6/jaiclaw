package io.jaiclaw.calendar.util

import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DateParsingUtilSpec extends Specification {

    def "parseStartDate parses full ISO datetime"() {
        when:
        def result = DateParsingUtil.parseStartDate("2024-06-15T10:00:00Z")

        then:
        result == Instant.parse("2024-06-15T10:00:00Z")
    }

    def "parseStartDate parses date-only as start of day"() {
        when:
        def result = DateParsingUtil.parseStartDate("2024-06-15")

        then:
        result == LocalDate.of(2024, 6, 15).atStartOfDay(ZoneOffset.UTC).toInstant()
    }

    def "parseEndDate parses date-only as end of day"() {
        when:
        def result = DateParsingUtil.parseEndDate("2024-06-15")

        then:
        result == LocalDate.of(2024, 6, 15).atTime(23, 59, 59, 999_999_999)
                .atZone(ZoneOffset.UTC).toInstant()
    }

    def "parseEndDate parses full ISO datetime"() {
        when:
        def result = DateParsingUtil.parseEndDate("2024-06-15T17:00:00Z")

        then:
        result == Instant.parse("2024-06-15T17:00:00Z")
    }

    def "null input throws IllegalArgumentException"() {
        when:
        DateParsingUtil.parseStartDate(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "blank input throws IllegalArgumentException"() {
        when:
        DateParsingUtil.parseStartDate("  ")

        then:
        thrown(IllegalArgumentException)
    }

    def "invalid format throws DateTimeParseException"() {
        when:
        DateParsingUtil.parseStartDate("not-a-date")

        then:
        thrown(Exception)
    }
}
