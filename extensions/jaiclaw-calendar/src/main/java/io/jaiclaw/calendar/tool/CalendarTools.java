package io.jaiclaw.calendar.tool;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import io.jaiclaw.calendar.config.CalendarProperties;
import io.jaiclaw.calendar.service.CalendarService;
import io.jaiclaw.calendar.util.CalendarEventValidator;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.ToolRegistry;

import java.util.List;

/**
 * Factory for creating and registering all calendar tools.
 */
public final class CalendarTools {

    private CalendarTools() {}

    public static List<ToolCallback> all(CalendarService calendarService, CalendarProperties properties,
                                         ObjectMapper objectMapper, CalendarEventValidator validator) {
        return List.of(
                new CreateEventTool(calendarService, properties, objectMapper, validator),
                new ListEventsTool(calendarService, properties, objectMapper),
                new UpdateEventTool(calendarService, properties, objectMapper),
                new DeleteEventTool(calendarService, properties, objectMapper),
                new GetAvailableSlotsTool(calendarService, properties, objectMapper),
                new ListCalendarsTool(calendarService, properties, objectMapper),
                new CreateCalendarTool(calendarService, properties, objectMapper),
                new TimeLookupTool(calendarService, properties, objectMapper)
        );
    }

    public static void registerAll(ToolRegistry registry, CalendarService calendarService,
                                   CalendarProperties properties, ObjectMapper objectMapper,
                                   CalendarEventValidator validator) {
        registry.registerAll(all(calendarService, properties, objectMapper, validator));
    }

    public static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
