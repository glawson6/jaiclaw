package io.jaiclaw.calendar.tool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
