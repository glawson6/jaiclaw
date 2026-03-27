package io.jaiclaw.calendar.model

import spock.lang.Specification

import java.time.Instant

class CalendarEventSpec extends Specification {

    def "builder creates event with all fields"() {
        given:
        def start = Instant.parse("2024-06-15T10:00:00Z")
        def end = Instant.parse("2024-06-15T11:00:00Z")

        when:
        def event = CalendarEvent.builder()
                .id("e1")
                .tenantId("t1")
                .calendarId("c1")
                .email("test@example.com")
                .title("Team Meeting")
                .description("Weekly sync")
                .timezone("UTC")
                .startTime(start)
                .endTime(end)
                .location("Room A")
                .organizer("org@example.com")
                .status(EventStatus.CONFIRMED)
                .allDay(false)
                .build()

        then:
        event.id() == "e1"
        event.tenantId() == "t1"
        event.calendarId() == "c1"
        event.title() == "Team Meeting"
        event.description() == "Weekly sync"
        event.startTime() == start
        event.endTime() == end
        event.location() == "Room A"
        event.status() == EventStatus.CONFIRMED
        !event.allDay()
    }

    def "overlapsWith detects overlapping time ranges"() {
        given:
        def event = CalendarEvent.builder()
                .startTime(Instant.parse("2024-06-15T10:00:00Z"))
                .endTime(Instant.parse("2024-06-15T11:00:00Z"))
                .title("Test")
                .build()

        expect:
        event.overlapsWith(Instant.parse("2024-06-15T09:30:00Z"), Instant.parse("2024-06-15T10:30:00Z"))
        event.overlapsWith(Instant.parse("2024-06-15T10:30:00Z"), Instant.parse("2024-06-15T11:30:00Z"))
        event.overlapsWith(Instant.parse("2024-06-15T09:00:00Z"), Instant.parse("2024-06-15T12:00:00Z"))
        !event.overlapsWith(Instant.parse("2024-06-15T11:01:00Z"), Instant.parse("2024-06-15T12:00:00Z"))
        !event.overlapsWith(Instant.parse("2024-06-15T08:00:00Z"), Instant.parse("2024-06-15T09:59:59Z"))
    }

    def "getDurationInSeconds returns correct duration"() {
        given:
        def event = CalendarEvent.builder()
                .startTime(Instant.parse("2024-06-15T10:00:00Z"))
                .endTime(Instant.parse("2024-06-15T11:00:00Z"))
                .title("Test")
                .build()

        expect:
        event.getDurationInSeconds() == 3600
    }

    def "builder defaults are sensible"() {
        when:
        def event = CalendarEvent.builder()
                .title("Test")
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .build()

        then:
        event.status() == EventStatus.CONFIRMED
        !event.allDay()
        event.description() == ""
        event.attendees() == []
        event.metadata() == [:]
    }
}
