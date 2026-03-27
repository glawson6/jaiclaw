package io.jaiclaw.calendar.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.calendar.config.CalendarProperties;
import io.jaiclaw.calendar.model.CalendarInfo;
import io.jaiclaw.calendar.service.CalendarService;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ListCalendarsTool extends AbstractCalendarTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "tenantId":{"type":"string","description":"Tenant ID (optional)"}
            }}""";

    public ListCalendarsTool(CalendarService calendarService, CalendarProperties properties, ObjectMapper objectMapper) {
        super(new ToolDefinition("calendar_list_calendars",
                "List all calendars for a tenant",
                ToolCatalog.SECTION_CALENDAR, INPUT_SCHEMA), calendarService, properties, objectMapper);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext context) throws Exception {
        String tenantId = resolveTenantId(params);

        List<CalendarInfo> calendars = calendarService.listCalendars(tenantId)
                .collectList().toFuture().get(30, TimeUnit.SECONDS);

        return jsonSuccess(Map.of("success", true, "calendars", calendars, "count", calendars.size()));
    }
}
