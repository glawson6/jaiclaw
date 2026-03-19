package io.jclaw.cron

import spock.lang.Specification

import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId

class CronScheduleComputerSpec extends Specification {

    CronScheduleComputer computer = new CronScheduleComputer()

    def "parses 5-field cron expression"() {
        when:
        def fields = computer.parseCron("0 9 * * *")

        then:
        fields == [0, 9, -1, -1, -1] as int[]
    }

    def "throws on invalid cron expression"() {
        when:
        computer.parseCron("invalid")

        then:
        thrown(IllegalArgumentException)
    }

    def "computes next fire time for daily 9 AM"() {
        given:
        def after = ZonedDateTime.of(2026, 3, 18, 8, 0, 0, 0, ZoneId.of("UTC")).toInstant()

        when:
        def next = computer.nextFireTime("0 9 * * *", "UTC", after)

        then:
        next.isPresent()
        def zdt = next.get().atZone(ZoneId.of("UTC"))
        zdt.getHour() == 9
        zdt.getMinute() == 0
    }

    def "matches correctly for wildcard fields"() {
        given:
        def dt = ZonedDateTime.of(2026, 3, 18, 9, 0, 0, 0, ZoneId.of("UTC"))
        def fields = computer.parseCron("0 9 * * *")

        expect:
        computer.matches(dt, fields)
    }

    def "does not match incorrect hour"() {
        given:
        def dt = ZonedDateTime.of(2026, 3, 18, 10, 0, 0, 0, ZoneId.of("UTC"))
        def fields = computer.parseCron("0 9 * * *")

        expect:
        !computer.matches(dt, fields)
    }

    def "returns empty for impossible schedule"() {
        when:
        def result = computer.nextFireTime("99 99 99 99 99", "UTC")

        then:
        result.isEmpty()
    }
}
