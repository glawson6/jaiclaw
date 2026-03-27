package io.jaiclaw.calendar.tool

import com.fasterxml.jackson.databind.ObjectMapper
import io.jaiclaw.calendar.config.CalendarProperties
import io.jaiclaw.calendar.provider.InMemoryCalendarProvider
import io.jaiclaw.calendar.service.CalendarService
import io.jaiclaw.calendar.util.CalendarEventValidator
import io.jaiclaw.tools.ToolCatalog
import io.jaiclaw.tools.ToolRegistry
import spock.lang.Specification

class CalendarToolsSpec extends Specification {

    CalendarService calendarService
    CalendarProperties properties
    ObjectMapper objectMapper
    CalendarEventValidator validator

    def setup() {
        def provider = new InMemoryCalendarProvider()
        properties = new CalendarProperties()
        calendarService = new CalendarService(provider)
        objectMapper = CalendarTools.createObjectMapper()
        validator = new CalendarEventValidator(properties)
    }

    def "all() returns exactly 8 tools"() {
        when:
        def tools = CalendarTools.all(calendarService, properties, objectMapper, validator)

        then:
        tools.size() == 8
    }

    def "all tools are in Calendar section"() {
        when:
        def tools = CalendarTools.all(calendarService, properties, objectMapper, validator)

        then:
        tools.every { it.definition().section() == ToolCatalog.SECTION_CALENDAR }
    }

    def "all tool names start with calendar_"() {
        when:
        def tools = CalendarTools.all(calendarService, properties, objectMapper, validator)

        then:
        tools.every { it.definition().name().startsWith("calendar_") }
    }

    def "tool names are unique"() {
        when:
        def tools = CalendarTools.all(calendarService, properties, objectMapper, validator)
        def names = tools.collect { it.definition().name() }

        then:
        names.unique().size() == names.size()
    }

    def "registerAll adds all tools to registry"() {
        given:
        def registry = new ToolRegistry()

        when:
        CalendarTools.registerAll(registry, calendarService, properties, objectMapper, validator)

        then:
        registry.size() == 8
        registry.contains("calendar_create_event")
        registry.contains("calendar_list_events")
        registry.contains("calendar_update_event")
        registry.contains("calendar_delete_event")
        registry.contains("calendar_get_available_slots")
        registry.contains("calendar_list_calendars")
        registry.contains("calendar_create_calendar")
        registry.contains("calendar_time_lookup")
    }

    def "all tools have non-blank descriptions"() {
        when:
        def tools = CalendarTools.all(calendarService, properties, objectMapper, validator)

        then:
        tools.every { !it.definition().description().isBlank() }
    }

    def "all tools have valid input schema"() {
        when:
        def tools = CalendarTools.all(calendarService, properties, objectMapper, validator)

        then:
        tools.every { it.definition().inputSchema().contains('"type"') }
    }
}
