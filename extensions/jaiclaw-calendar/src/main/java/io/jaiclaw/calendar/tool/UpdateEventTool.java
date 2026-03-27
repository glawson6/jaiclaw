package io.jaiclaw.calendar.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.calendar.config.CalendarProperties;
import io.jaiclaw.calendar.model.CalendarEvent;
import io.jaiclaw.calendar.service.CalendarService;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class UpdateEventTool extends AbstractCalendarTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "eventId":{"type":"string","description":"Event ID to update"},
            "tenantId":{"type":"string","description":"Tenant ID (optional)"},
            "calendarId":{"type":"string","description":"Calendar ID (optional)"},
            "title":{"type":"string","description":"New title"},
            "description":{"type":"string","description":"New description"},
            "startTime":{"type":"string","description":"New start time ISO 8601"},
            "endTime":{"type":"string","description":"New end time ISO 8601"},
            "location":{"type":"string","description":"New location"},
            "status":{"type":"string","description":"New status: CONFIRMED, TENTATIVE, CANCELLED"}
            },"required":["eventId"]}""";

    public UpdateEventTool(CalendarService calendarService, CalendarProperties properties, ObjectMapper objectMapper) {
        super(new ToolDefinition("calendar_update_event",
                "Update an existing calendar event",
                ToolCatalog.SECTION_CALENDAR, INPUT_SCHEMA), calendarService, properties, objectMapper);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext context) throws Exception {
        String eventId = requireParam(params, "eventId");
        String tenantId = resolveTenantId(params);
        String calendarId = resolveCalendarId(params);

        Map<String, Object> updates = new HashMap<>();
        for (String field : new String[]{"title", "description", "startTime", "endTime", "location", "status"}) {
            Object value = params.get(field);
            if (value != null && !value.toString().isBlank()) {
                updates.put(field, value.toString());
            }
        }

        if (updates.isEmpty()) {
            return jsonSuccess(Map.of("success", false, "error", "No update fields provided"));
        }

        if (updates.containsKey("startTime") && updates.containsKey("endTime")) {
            Instant start = Instant.parse((String) updates.get("startTime"));
            Instant end = Instant.parse((String) updates.get("endTime"));
            if (end.isBefore(start)) {
                return jsonSuccess(Map.of("success", false, "error", "End time must be after start time"));
            }
        }

        CalendarEvent updated = calendarService.updateEvent(tenantId, calendarId, eventId, updates)
                .toFuture().get(30, TimeUnit.SECONDS);

        return jsonSuccess(Map.of("success", true, "event", updated));
    }
}
