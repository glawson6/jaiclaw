package io.jaiclaw.calendar.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.calendar.config.CalendarProperties;
import io.jaiclaw.calendar.model.CalendarEvent;
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

public class ListEventsTool extends AbstractCalendarTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "startDate":{"type":"string","description":"Start date ISO 8601"},
            "endDate":{"type":"string","description":"End date ISO 8601"},
            "tenantId":{"type":"string","description":"Tenant ID (optional)"},
            "calendarId":{"type":"string","description":"Calendar ID (optional)"},
            "limit":{"type":"integer","description":"Max events to return (default 5)"}
            },"required":["startDate","endDate"]}""";

    public ListEventsTool(CalendarService calendarService, CalendarProperties properties, ObjectMapper objectMapper) {
        super(new ToolDefinition("calendar_list_events",
                "List calendar events within a date range",
                ToolCatalog.SECTION_CALENDAR, INPUT_SCHEMA), calendarService, properties, objectMapper);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext context) throws Exception {
        String startDate = requireParam(params, "startDate");
        String endDate = requireParam(params, "endDate");
        String tenantId = resolveTenantId(params);
        String calendarId = resolveCalendarId(params);
        Object limitObj = params.get("limit");
        Integer limit = limitObj != null ? ((Number) limitObj).intValue() : 5;
        if (limit == 0) limit = 5;

        Instant start = DateParsingUtil.parseStartDate(startDate);
        Instant end = DateParsingUtil.parseEndDate(endDate);

        if (end.isBefore(start)) {
            return jsonSuccess(Map.of("success", false, "error", "End date must be after start date"));
        }

        List<CalendarEvent> events = calendarService.listEvents(tenantId, calendarId, start, end, limit)
                .collectList().toFuture().get(30, TimeUnit.SECONDS);

        return jsonSuccess(Map.of("success", true, "events", events, "count", events.size()));
    }
}
