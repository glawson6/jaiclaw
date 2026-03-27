package io.jaiclaw.calendar.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.calendar.config.CalendarProperties;
import io.jaiclaw.calendar.service.CalendarService;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DeleteEventTool extends AbstractCalendarTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "eventId":{"type":"string","description":"Event ID to delete"},
            "tenantId":{"type":"string","description":"Tenant ID (optional)"},
            "calendarId":{"type":"string","description":"Calendar ID (optional)"}
            },"required":["eventId"]}""";

    public DeleteEventTool(CalendarService calendarService, CalendarProperties properties, ObjectMapper objectMapper) {
        super(new ToolDefinition("calendar_delete_event",
                "Delete a calendar event by ID",
                ToolCatalog.SECTION_CALENDAR, INPUT_SCHEMA), calendarService, properties, objectMapper);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext context) throws Exception {
        String eventId = requireParam(params, "eventId");
        String tenantId = resolveTenantId(params);
        String calendarId = resolveCalendarId(params);

        calendarService.deleteEvent(tenantId, calendarId, eventId)
                .toFuture().get(30, TimeUnit.SECONDS);

        return jsonSuccess(Map.of("success", true, "deleted", true, "eventId", eventId));
    }
}
