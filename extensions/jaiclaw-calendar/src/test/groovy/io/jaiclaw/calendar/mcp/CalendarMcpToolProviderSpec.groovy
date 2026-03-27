package io.jaiclaw.calendar.mcp

import io.jaiclaw.calendar.config.CalendarProperties
import io.jaiclaw.calendar.provider.InMemoryCalendarProvider
import io.jaiclaw.calendar.service.CalendarService
import io.jaiclaw.calendar.tool.CalendarTools
import io.jaiclaw.calendar.util.CalendarEventValidator
import spock.lang.Specification

import java.time.Instant

class CalendarMcpToolProviderSpec extends Specification {

    CalendarMcpToolProvider provider

    def setup() {
        def calendarProvider = new InMemoryCalendarProvider()
        def properties = new CalendarProperties()
        def calendarService = new CalendarService(calendarProvider)
        def validator = new CalendarEventValidator(properties)
        def objectMapper = CalendarTools.createObjectMapper()
        provider = new CalendarMcpToolProvider(calendarService, properties, validator, objectMapper)
    }

    def "server name is calendar"() {
        expect:
        provider.getServerName() == "calendar"
    }

    def "provides 8 tools"() {
        expect:
        provider.getTools().size() == 8
    }

    def "tool names are correct"() {
        when:
        def toolNames = provider.getTools().collect { it.name() }

        then:
        toolNames.containsAll(["create_event", "list_events", "update_event", "delete_event",
                                "get_available_slots", "list_calendars", "create_calendar", "time_lookup"])
    }

    def "time_lookup returns current time"() {
        when:
        def result = provider.execute("time_lookup", [:], null)

        then:
        !result.isError()
        result.content().contains("currentTime")
        result.content().contains("utcTime")
    }

    def "list_events returns events"() {
        when:
        def result = provider.execute("list_events", [
                startDate: "2024-01-01",
                endDate: "2030-12-31"
        ], null)

        then:
        !result.isError()
        result.content().contains("success")
    }

    def "create_event with explicit times"() {
        given:
        def start = Instant.now().plusSeconds(86400)
        def end = start.plusSeconds(3600)

        when:
        def result = provider.execute("create_event", [
                title: "Test Event",
                email: "test@example.com",
                organizer: "Organizer",
                startTime: start.toString(),
                endTime: end.toString()
        ], null)

        then:
        !result.isError()
        result.content().contains("explicit")
        result.content().contains("Test Event")
    }

    def "create_event rejects missing title"() {
        when:
        def result = provider.execute("create_event", [
                email: "test@example.com",
                organizer: "Organizer"
        ], null)

        then:
        result.isError()
        result.content().contains("title")
    }

    def "delete_event executes"() {
        when:
        def result = provider.execute("delete_event", [
                eventId: "nonexistent"
        ], null)

        then:
        // In-memory provider silently ignores missing events
        !result.isError()
    }

    def "unknown tool returns error"() {
        when:
        def result = provider.execute("nonexistent_tool", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }

    def "update_event rejects empty updates"() {
        when:
        def result = provider.execute("update_event", [eventId: "e1"], null)

        then:
        result.isError()
        result.content().contains("No update fields")
    }

    def "create_calendar succeeds"() {
        when:
        def result = provider.execute("create_calendar", [
                calendarId: "test-cal",
                name: "Test Calendar"
        ], null)

        then:
        !result.isError()
        result.content().contains("test-cal")
    }

    def "list_calendars after creating one"() {
        given:
        provider.execute("create_calendar", [calendarId: "cal-1", name: "Cal 1"], null)

        when:
        def result = provider.execute("list_calendars", [:], null)

        then:
        !result.isError()
        result.content().contains("cal-1")
    }
}
