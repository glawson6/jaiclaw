package io.jaiclaw.calendar.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.calendar.config.CalendarProperties;
import io.jaiclaw.calendar.service.CalendarService;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class TimeLookupTool extends AbstractCalendarTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "timezone":{"type":"string","description":"Timezone (e.g., America/New_York). Defaults to UTC."}
            }}""";

    public TimeLookupTool(CalendarService calendarService, CalendarProperties properties, ObjectMapper objectMapper) {
        super(new ToolDefinition("calendar_time_lookup",
                "Get the current date and time",
                ToolCatalog.SECTION_CALENDAR, INPUT_SCHEMA), calendarService, properties, objectMapper);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext context) throws Exception {
        String timezone = optionalParam(params, "timezone", "UTC");
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            zoneId = ZoneId.of("UTC");
        }
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return jsonSuccess(Map.of(
                "currentTime", now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                "timezone", zoneId.getId(),
                "utcTime", Instant.now().toString()
        ));
    }
}
