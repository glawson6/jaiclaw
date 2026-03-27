package io.jaiclaw.calendar.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.calendar.config.CalendarProperties;
import io.jaiclaw.calendar.service.CalendarService;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;

/**
 * Base class for calendar tools providing common helpers.
 */
public abstract class AbstractCalendarTool extends AbstractBuiltinTool {

    protected final CalendarService calendarService;
    protected final CalendarProperties properties;
    protected final ObjectMapper objectMapper;

    protected AbstractCalendarTool(ToolDefinition definition, CalendarService calendarService,
                                   CalendarProperties properties, ObjectMapper objectMapper) {
        super(definition);
        this.calendarService = calendarService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    protected String resolveTenantId(Map<String, Object> params) {
        Object tenantId = params.get("tenantId");
        if (tenantId != null && !tenantId.toString().isBlank()) {
            return tenantId.toString();
        }
        return properties.defaultTenantId();
    }

    protected String resolveCalendarId(Map<String, Object> params) {
        Object calendarId = params.get("calendarId");
        if (calendarId != null && !calendarId.toString().isBlank()) {
            return calendarId.toString();
        }
        return properties.defaultCalendarName();
    }

    protected String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    protected ToolResult jsonSuccess(Map<String, Object> data) {
        try {
            return new ToolResult.Success(toJson(data));
        } catch (JsonProcessingException e) {
            return new ToolResult.Error("JSON serialization failed: " + e.getMessage());
        }
    }
}
