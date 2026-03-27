package io.jaiclaw.calendar.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.calendar.config.CalendarProperties;
import io.jaiclaw.calendar.model.AppointmentRequest;
import io.jaiclaw.calendar.model.CalendarEvent;
import io.jaiclaw.calendar.model.CalendarInfo;
import io.jaiclaw.calendar.model.TimeSlot;
import io.jaiclaw.calendar.service.CalendarService;
import io.jaiclaw.calendar.util.CalendarEventValidator;
import io.jaiclaw.calendar.util.DateParsingUtil;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool provider exposing calendar management tools.
 * Server name: {@code calendar}, with 8 tools for event CRUD, scheduling, and calendar management.
 */
public class CalendarMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(CalendarMcpToolProvider.class);
    private static final String SERVER_NAME = "calendar";
    private static final String SERVER_DESCRIPTION = "Calendar management — create, list, update, delete events, find available slots, and manage calendars";

    private final CalendarService calendarService;
    private final CalendarProperties properties;
    private final CalendarEventValidator validator;
    private final ObjectMapper objectMapper;

    public CalendarMcpToolProvider(CalendarService calendarService, CalendarProperties properties,
                                   CalendarEventValidator validator, ObjectMapper objectMapper) {
        this.calendarService = calendarService;
        this.properties = properties;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getServerName() { return SERVER_NAME; }

    @Override
    public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("create_event", "Create a calendar event with explicit times or auto-scheduling", CREATE_EVENT_SCHEMA),
                new McpToolDefinition("list_events", "List calendar events within a date range", LIST_EVENTS_SCHEMA),
                new McpToolDefinition("update_event", "Update an existing calendar event", UPDATE_EVENT_SCHEMA),
                new McpToolDefinition("delete_event", "Delete a calendar event by ID", DELETE_EVENT_SCHEMA),
                new McpToolDefinition("get_available_slots", "Find available time slots", GET_SLOTS_SCHEMA),
                new McpToolDefinition("list_calendars", "List all calendars for a tenant", LIST_CALENDARS_SCHEMA),
                new McpToolDefinition("create_calendar", "Create a new calendar", CREATE_CALENDAR_SCHEMA),
                new McpToolDefinition("time_lookup", "Get current date and time", TIME_LOOKUP_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        try {
            return switch (toolName) {
                case "create_event" -> handleCreateEvent(args);
                case "list_events" -> handleListEvents(args);
                case "update_event" -> handleUpdateEvent(args);
                case "delete_event" -> handleDeleteEvent(args);
                case "get_available_slots" -> handleGetAvailableSlots(args);
                case "list_calendars" -> handleListCalendars(args);
                case "create_calendar" -> handleCreateCalendar(args);
                case "time_lookup" -> handleTimeLookup(args);
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.error("MCP tool execution failed: {}", toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    private McpToolResult handleCreateEvent(Map<String, Object> args) throws Exception {
        String title = requireString(args, "title");
        String email = requireString(args, "email");
        String organizer = requireString(args, "organizer");
        String tenantId = stringOrDefault(args, "tenantId", properties.defaultTenantId());
        String calendarId = stringOrDefault(args, "calendarId", properties.defaultCalendarName());
        String description = stringOrDefault(args, "description", "");
        String location = stringOrDefault(args, "location", "");
        String startTime = (String) args.get("startTime");
        String endTime = (String) args.get("endTime");
        Number durationObj = (Number) args.get("durationMinutes");

        boolean hasExplicit = startTime != null && endTime != null;
        if (hasExplicit) {
            Instant start = Instant.parse(startTime);
            Instant end = Instant.parse(endTime);
            Optional<String> error = validator.validateEventTiming(start, end);
            if (error.isPresent()) return McpToolResult.error(error.get());

            CalendarEvent event = CalendarEvent.builder()
                    .tenantId(tenantId).calendarId(calendarId).title(title).email(email)
                    .organizer(organizer).description(description).location(location)
                    .startTime(start).endTime(end).build();
            CalendarEvent created = calendarService.createEvent(event).toFuture().get(30, TimeUnit.SECONDS);
            return McpToolResult.success(toJson(Map.of("success", true, "mode", "explicit", "event", created)));
        } else if (durationObj != null) {
            long durationSeconds = durationObj.longValue() * 60;
            Instant earliest = args.get("earliestTime") != null ? DateParsingUtil.parseStartDate((String) args.get("earliestTime")) : Instant.now();
            Instant latest = args.get("latestTime") != null ? DateParsingUtil.parseEndDate((String) args.get("latestTime")) : earliest.plusSeconds(30L * 24 * 3600);
            AppointmentRequest request = AppointmentRequest.builder().title(title).email(email).organizer(organizer)
                    .description(description).location(location).durationInSeconds(durationSeconds)
                    .earliestTime(earliest).latestTime(latest).findEarliestSlot(true).build();
            CalendarEvent scheduled = calendarService.scheduleAppointment(request).toFuture().get(30, TimeUnit.SECONDS);
            return McpToolResult.success(toJson(Map.of("success", true, "mode", "auto-schedule", "event", scheduled)));
        } else {
            return McpToolResult.error("Provide startTime+endTime or durationMinutes");
        }
    }

    private McpToolResult handleListEvents(Map<String, Object> args) throws Exception {
        String tenantId = stringOrDefault(args, "tenantId", properties.defaultTenantId());
        String calendarId = stringOrDefault(args, "calendarId", properties.defaultCalendarName());
        Instant start = DateParsingUtil.parseStartDate(requireString(args, "startDate"));
        Instant end = DateParsingUtil.parseEndDate(requireString(args, "endDate"));
        Number limitObj = (Number) args.get("limit");
        Integer limit = limitObj != null ? limitObj.intValue() : 5;
        List<CalendarEvent> events = calendarService.listEvents(tenantId, calendarId, start, end, limit)
                .collectList().toFuture().get(30, TimeUnit.SECONDS);
        return McpToolResult.success(toJson(Map.of("success", true, "events", events, "count", events.size())));
    }

    private McpToolResult handleUpdateEvent(Map<String, Object> args) throws Exception {
        String eventId = requireString(args, "eventId");
        String tenantId = stringOrDefault(args, "tenantId", properties.defaultTenantId());
        String calendarId = stringOrDefault(args, "calendarId", properties.defaultCalendarName());
        Map<String, Object> updates = new HashMap<>();
        for (String field : new String[]{"title", "description", "startTime", "endTime", "location", "status"}) {
            Object v = args.get(field);
            if (v != null) updates.put(field, v.toString());
        }
        if (updates.isEmpty()) return McpToolResult.error("No update fields provided");
        CalendarEvent updated = calendarService.updateEvent(tenantId, calendarId, eventId, updates)
                .toFuture().get(30, TimeUnit.SECONDS);
        return McpToolResult.success(toJson(Map.of("success", true, "event", updated)));
    }

    private McpToolResult handleDeleteEvent(Map<String, Object> args) throws Exception {
        String eventId = requireString(args, "eventId");
        String tenantId = stringOrDefault(args, "tenantId", properties.defaultTenantId());
        String calendarId = stringOrDefault(args, "calendarId", properties.defaultCalendarName());
        calendarService.deleteEvent(tenantId, calendarId, eventId).toFuture().get(30, TimeUnit.SECONDS);
        return McpToolResult.success(toJson(Map.of("success", true, "deleted", true, "eventId", eventId)));
    }

    private McpToolResult handleGetAvailableSlots(Map<String, Object> args) throws Exception {
        Instant start = DateParsingUtil.parseStartDate(requireString(args, "startDate"));
        Instant end = DateParsingUtil.parseEndDate(requireString(args, "endDate"));
        long durationMinutes = ((Number) args.get("durationMinutes")).longValue();
        List<TimeSlot> slots = calendarService.getAvailableSlots(start, end, durationMinutes * 60)
                .collectList().toFuture().get(30, TimeUnit.SECONDS);
        return McpToolResult.success(toJson(Map.of("success", true, "slots", slots, "count", slots.size())));
    }

    private McpToolResult handleListCalendars(Map<String, Object> args) throws Exception {
        String tenantId = stringOrDefault(args, "tenantId", properties.defaultTenantId());
        List<CalendarInfo> calendars = calendarService.listCalendars(tenantId)
                .collectList().toFuture().get(30, TimeUnit.SECONDS);
        return McpToolResult.success(toJson(Map.of("success", true, "calendars", calendars, "count", calendars.size())));
    }

    private McpToolResult handleCreateCalendar(Map<String, Object> args) throws Exception {
        String calendarId = requireString(args, "calendarId");
        String name = requireString(args, "name");
        String tenantId = stringOrDefault(args, "tenantId", properties.defaultTenantId());
        CalendarInfo calendar = CalendarInfo.builder()
                .id(calendarId).tenantId(tenantId).name(name)
                .description(stringOrDefault(args, "description", ""))
                .color(stringOrDefault(args, "color", "#667eea"))
                .timezone(stringOrDefault(args, "timezone", "UTC"))
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        CalendarInfo created = calendarService.createCalendar(calendar).toFuture().get(30, TimeUnit.SECONDS);
        return McpToolResult.success(toJson(Map.of("success", true, "calendar", created)));
    }

    private McpToolResult handleTimeLookup(Map<String, Object> args) throws Exception {
        String tz = stringOrDefault(args, "timezone", "UTC");
        ZoneId zoneId;
        try { zoneId = ZoneId.of(tz); } catch (Exception e) { zoneId = ZoneId.of("UTC"); }
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return McpToolResult.success(toJson(Map.of(
                "currentTime", now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                "timezone", zoneId.getId(), "utcTime", Instant.now().toString())));
    }

    private String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) throw new IllegalArgumentException("Missing required parameter: " + key);
        return value.toString();
    }

    private String stringOrDefault(Map<String, Object> args, String key, String defaultValue) {
        Object value = args.get(key);
        return (value != null && !value.toString().isBlank()) ? value.toString() : defaultValue;
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    // --- JSON Schema constants ---
    private static final String CREATE_EVENT_SCHEMA = """
            {"type":"object","properties":{"title":{"type":"string"},"email":{"type":"string"},"organizer":{"type":"string"},\
            "description":{"type":"string"},"location":{"type":"string"},"tenantId":{"type":"string"},"calendarId":{"type":"string"},\
            "startTime":{"type":"string"},"endTime":{"type":"string"},"durationMinutes":{"type":"integer"},\
            "earliestTime":{"type":"string"},"latestTime":{"type":"string"}},"required":["title","email","organizer"]}""";

    private static final String LIST_EVENTS_SCHEMA = """
            {"type":"object","properties":{"startDate":{"type":"string"},"endDate":{"type":"string"},\
            "tenantId":{"type":"string"},"calendarId":{"type":"string"},"limit":{"type":"integer"}},"required":["startDate","endDate"]}""";

    private static final String UPDATE_EVENT_SCHEMA = """
            {"type":"object","properties":{"eventId":{"type":"string"},"tenantId":{"type":"string"},"calendarId":{"type":"string"},\
            "title":{"type":"string"},"description":{"type":"string"},"startTime":{"type":"string"},"endTime":{"type":"string"},\
            "location":{"type":"string"},"status":{"type":"string"}},"required":["eventId"]}""";

    private static final String DELETE_EVENT_SCHEMA = """
            {"type":"object","properties":{"eventId":{"type":"string"},"tenantId":{"type":"string"},\
            "calendarId":{"type":"string"}},"required":["eventId"]}""";

    private static final String GET_SLOTS_SCHEMA = """
            {"type":"object","properties":{"startDate":{"type":"string"},"endDate":{"type":"string"},\
            "durationMinutes":{"type":"integer"}},"required":["startDate","endDate","durationMinutes"]}""";

    private static final String LIST_CALENDARS_SCHEMA = """
            {"type":"object","properties":{"tenantId":{"type":"string"}}}""";

    private static final String CREATE_CALENDAR_SCHEMA = """
            {"type":"object","properties":{"calendarId":{"type":"string"},"name":{"type":"string"},\
            "tenantId":{"type":"string"},"description":{"type":"string"},"color":{"type":"string"},\
            "timezone":{"type":"string"}},"required":["calendarId","name"]}""";

    private static final String TIME_LOOKUP_SCHEMA = """
            {"type":"object","properties":{"timezone":{"type":"string"}}}""";
}
