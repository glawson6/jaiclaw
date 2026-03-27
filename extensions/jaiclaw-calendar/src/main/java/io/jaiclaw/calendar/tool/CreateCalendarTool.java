package io.jaiclaw.calendar.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.calendar.config.CalendarProperties;
import io.jaiclaw.calendar.model.CalendarInfo;
import io.jaiclaw.calendar.service.CalendarService;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CreateCalendarTool extends AbstractCalendarTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "calendarId":{"type":"string","description":"Calendar ID (lowercase letters/numbers/hyphens)"},
            "name":{"type":"string","description":"Calendar display name"},
            "tenantId":{"type":"string","description":"Tenant ID (optional)"},
            "description":{"type":"string","description":"Calendar description"},
            "color":{"type":"string","description":"Color hex (default #667eea)"},
            "timezone":{"type":"string","description":"Timezone (default UTC)"}
            },"required":["calendarId","name"]}""";

    public CreateCalendarTool(CalendarService calendarService, CalendarProperties properties, ObjectMapper objectMapper) {
        super(new ToolDefinition("calendar_create_calendar",
                "Create a new calendar for a tenant",
                ToolCatalog.SECTION_CALENDAR, INPUT_SCHEMA), calendarService, properties, objectMapper);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext context) throws Exception {
        String calendarId = requireParam(params, "calendarId");
        String name = requireParam(params, "name");
        String tenantId = resolveTenantId(params);
        String description = optionalParam(params, "description", "");
        String color = optionalParam(params, "color", "#667eea");
        String timezone = optionalParam(params, "timezone", "UTC");

        if (!calendarId.matches("^[a-z0-9-]+$")) {
            return jsonSuccess(Map.of("success", false,
                    "error", "Calendar ID must contain only lowercase letters, numbers, and hyphens"));
        }

        CalendarInfo calendar = CalendarInfo.builder()
                .id(calendarId)
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .color(color)
                .timezone(timezone)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        CalendarInfo created = calendarService.createCalendar(calendar)
                .toFuture().get(30, TimeUnit.SECONDS);

        return jsonSuccess(Map.of("success", true, "calendar", created));
    }
}
