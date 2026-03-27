package io.jaiclaw.calendar.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.calendar.config.CalendarProperties;
import io.jaiclaw.calendar.model.TimeSlot;
import io.jaiclaw.calendar.service.CalendarService;
import io.jaiclaw.calendar.util.DateParsingUtil;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GetAvailableSlotsTool extends AbstractCalendarTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "startDate":{"type":"string","description":"Start date ISO 8601"},
            "endDate":{"type":"string","description":"End date ISO 8601"},
            "durationMinutes":{"type":"integer","description":"Required duration in minutes"}
            },"required":["startDate","endDate","durationMinutes"]}""";

    public GetAvailableSlotsTool(CalendarService calendarService, CalendarProperties properties, ObjectMapper objectMapper) {
        super(new ToolDefinition("calendar_get_available_slots",
                "Find available time slots that can accommodate a meeting of specified duration",
                ToolCatalog.SECTION_CALENDAR, INPUT_SCHEMA), calendarService, properties, objectMapper);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext context) throws Exception {
        String startDate = requireParam(params, "startDate");
        String endDate = requireParam(params, "endDate");
        long durationMinutes = ((Number) params.get("durationMinutes")).longValue();

        Instant start = DateParsingUtil.parseStartDate(startDate);
        Instant end = DateParsingUtil.parseEndDate(endDate);

        if (end.isBefore(start)) {
            return jsonSuccess(Map.of("success", false, "error", "End date must be after start date"));
        }
        if (durationMinutes <= 0) {
            return jsonSuccess(Map.of("success", false, "error", "Duration must be positive"));
        }

        long durationSeconds = durationMinutes * 60;
        List<TimeSlot> slots = calendarService.getAvailableSlots(start, end, durationSeconds)
                .collectList().toFuture().get(30, TimeUnit.SECONDS);

        return jsonSuccess(Map.of("success", true, "slots", slots, "count", slots.size()));
    }
}
