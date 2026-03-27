package io.jaiclaw.calendar.provider

import io.jaiclaw.calendar.model.*
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class InMemoryCalendarProviderSpec extends Specification {

    InMemoryCalendarProvider provider

    def setup() {
        provider = new InMemoryCalendarProvider()
    }

    def "createEvent assigns ID and timestamps"() {
        given:
        def event = CalendarEvent.builder()
                .title("Test Event")
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .build()

        when:
        def created = provider.createEvent(event).block()

        then:
        created.id() != null
        created.title() == "Test Event"
        created.createdAt() != null
        created.updatedAt() != null
    }

    def "listEvents filters by date range"() {
        given:
        def now = Instant.now()
        provider.createEvent(CalendarEvent.builder()
                .title("Event 1")
                .startTime(now.plusSeconds(3600))
                .endTime(now.plusSeconds(7200))
                .build()).block()
        provider.createEvent(CalendarEvent.builder()
                .title("Event 2")
                .startTime(now.plusSeconds(86400))
                .endTime(now.plusSeconds(90000))
                .build()).block()

        when:
        def events = provider.listEvents(null, null,
                now, now.plusSeconds(10000), null)
                .collectList().block()

        then:
        events.size() == 1
        events[0].title() == "Event 1"
    }

    def "listEvents respects limit"() {
        given:
        def now = Instant.now()
        3.times { i ->
            provider.createEvent(CalendarEvent.builder()
                    .title("Event ${i}")
                    .startTime(now.plusSeconds(3600 * (i + 1)))
                    .endTime(now.plusSeconds(3600 * (i + 1) + 1800))
                    .build()).block()
        }

        when:
        def events = provider.listEvents(null, null,
                now, now.plusSeconds(86400), 2)
                .collectList().block()

        then:
        events.size() == 2
    }

    def "getEvent returns event by ID"() {
        given:
        def created = provider.createEvent(CalendarEvent.builder()
                .title("Find Me")
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .build()).block()

        when:
        def found = provider.getEvent(null, null, created.id()).block()

        then:
        found != null
        found.title() == "Find Me"
    }

    def "getEvent returns empty for unknown ID"() {
        when:
        def found = provider.getEvent(null, null, "nonexistent").block()

        then:
        found == null
    }

    def "updateEvent applies changes"() {
        given:
        def created = provider.createEvent(CalendarEvent.builder()
                .title("Original")
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .build()).block()

        when:
        def updated = provider.updateEvent(null, null, created.id(),
                [title: "Updated Title", location: "New Room"]).block()

        then:
        updated.title() == "Updated Title"
        updated.location() == "New Room"
    }

    def "deleteEvent removes event"() {
        given:
        def created = provider.createEvent(CalendarEvent.builder()
                .title("Delete Me")
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .build()).block()

        when:
        provider.deleteEvent(null, null, created.id()).block()
        def found = provider.getEvent(null, null, created.id()).block()

        then:
        found == null
    }

    def "getAvailableSlots finds gaps"() {
        given:
        def now = Instant.now()
        def start = now.plusSeconds(3600)
        // Create event from +1h to +2h
        provider.createEvent(CalendarEvent.builder()
                .title("Blocker")
                .startTime(start)
                .endTime(start.plusSeconds(3600))
                .build()).block()

        when:
        // Search from now to +4h for 30min slots
        def slots = provider.getAvailableSlots(now, now.plusSeconds(14400), 1800)
                .collectList().block()

        then:
        slots.size() >= 1
        slots.every { it.available() }
    }

    def "scheduleAppointment finds earliest slot"() {
        given:
        def request = AppointmentRequest.builder()
                .title("Auto-scheduled")
                .email("test@example.com")
                .durationInSeconds(1800L)
                .earliestTime(Instant.now())
                .latestTime(Instant.now().plus(Duration.ofDays(7)))
                .findEarliestSlot(true)
                .build()

        when:
        def scheduled = provider.scheduleAppointment(request).block()

        then:
        scheduled != null
        scheduled.title() == "Auto-scheduled"
        scheduled.getDurationInSeconds() == 1800
    }

    def "createCalendar and listCalendars work"() {
        given:
        def cal = CalendarInfo.builder()
                .id("team-cal")
                .tenantId("t1")
                .name("Team Calendar")
                .build()

        when:
        provider.createCalendar(cal).block()
        def calendars = provider.listCalendars("t1").collectList().block()

        then:
        calendars.size() == 1
        calendars[0].id() == "team-cal"
        calendars[0].name() == "Team Calendar"
    }

    def "createCalendar rejects duplicate ID"() {
        given:
        def cal = CalendarInfo.builder()
                .id("dup-cal")
                .tenantId("t1")
                .name("Calendar")
                .build()
        provider.createCalendar(cal).block()

        when:
        provider.createCalendar(cal).block()

        then:
        thrown(RuntimeException)
    }

    def "provider name is in-memory"() {
        expect:
        provider.getProviderName() == "in-memory"
    }

    def "clear removes all data"() {
        given:
        provider.createEvent(CalendarEvent.builder()
                .title("T")
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .build()).block()

        when:
        provider.clear()

        then:
        provider.getEventCount() == 0
    }
}
