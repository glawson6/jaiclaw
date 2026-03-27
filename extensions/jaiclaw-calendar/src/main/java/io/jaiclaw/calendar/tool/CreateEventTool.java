package io.jaiclaw.calendar.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.calendar.config.CalendarProperties;
import io.jaiclaw.calendar.model.AppointmentRequest;
import io.jaiclaw.calendar.model.CalendarEvent;
import io.jaiclaw.calendar.service.CalendarService;
import io.jaiclaw.calendar.util.CalendarEventValidator;
import io.jaiclaw.calendar.util.DateParsingUtil;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Unified calendar event creation tool supporting explicit times or auto-scheduling.
 */
public class CreateEventTool extends AbstractCalendarTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "title":{"type":"string","description":"Event title"},
            "email":{"type":"string","description":"Organizer email"},
            "organizer":{"type":"string","description":"Event organizer name"},
            "description":{"type":"string","description":"Event description"},
            "location":{"type":"string","description":"Event location"},
            "tenantId":{"type":"string","description":"Tenant ID (optional)"},
            "calendarId":{"type":"string","description":"Calendar ID (optional)"},
            "startTime":{"type":"string","description":"Start time ISO 8601 (explicit mode)"},
            "endTime":{"type":"string","description":"End time ISO 8601 (explicit mode)"},
            "durationMinutes":{"type":"integer","description":"Duration in minutes (auto-schedule mode)"},
            "earliestTime":{"type":"string","description":"Earliest time for auto-scheduling"},
            "latestTime":{"type":"string","description":"Latest time for auto-scheduling"}
            },"required":["title","email","organizer"]}""";

    private final CalendarEventValidator validator;

    public CreateEventTool(CalendarService calendarService, CalendarProperties properties,
                           ObjectMapper objectMapper, CalendarEventValidator validator) {
        super(new ToolDefinition("calendar_create_event",
                "Create a calendar event with explicit times or auto-scheduling",
                ToolCatalog.SECTION_CALENDAR, INPUT_SCHEMA), calendarService, properties, objectMapper);
        this.validator = validator;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext context) throws Exception {
        String title = requireParam(params, "title");
        String email = requireParam(params, "email");
        String organizer = requireParam(params, "organizer");
        String description = optionalParam(params, "description", "");
        String location = optionalParam(params, "location", "");
        String tenantId = resolveTenantId(params);
        String calendarId = resolveCalendarId(params);
        String startTime = optionalParam(params, "startTime", null);
        String endTime = optionalParam(params, "endTime", null);
        Object durationObj = params.get("durationMinutes");
        Long durationMinutes = durationObj != null ? ((Number) durationObj).longValue() : null;
        String earliestTime = optionalParam(params, "earliestTime", null);
        String latestTime = optionalParam(params, "latestTime", null);

        boolean hasExplicitTimes = startTime != null && endTime != null;

        if (hasExplicitTimes) {
            Instant start = Instant.parse(startTime);
            Instant end = Instant.parse(endTime);

            Optional<String> validationError = validator.validateEventTiming(start, end);
            if (validationError.isPresent()) {
                return jsonSuccess(Map.of("success", false, "error", validationError.get()));
            }

            CalendarEvent event = CalendarEvent.builder()
                    .tenantId(tenantId).calendarId(calendarId)
                    .title(title).email(email).organizer(organizer)
                    .description(description).location(location)
                    .startTime(start).endTime(end)
                    .build();

            CalendarEvent created = calendarService.createEvent(event)
                    .toFuture().get(30, TimeUnit.SECONDS);

            return jsonSuccess(Map.of("success", true, "mode", "explicit", "event", created));

        } else if (durationMinutes != null && durationMinutes > 0) {
            long durationSeconds = durationMinutes * 60;
            Instant earliest = earliestTime != null
                    ? DateParsingUtil.parseStartDate(earliestTime) : Instant.now();
            Instant latest = latestTime != null
                    ? DateParsingUtil.parseEndDate(latestTime) : earliest.plusSeconds(30L * 24 * 3600);

            AppointmentRequest request = AppointmentRequest.builder()
                    .title(title).email(email).organizer(organizer)
                    .description(description).location(location)
                    .durationInSeconds(durationSeconds)
                    .earliestTime(earliest).latestTime(latest)
                    .findEarliestSlot(true)
                    .build();

            CalendarEvent scheduled = calendarService.scheduleAppointment(request)
                    .toFuture().get(30, TimeUnit.SECONDS);

            return jsonSuccess(Map.of("success", true, "mode", "auto-schedule", "event", scheduled));
        } else {
            return jsonSuccess(Map.of("success", false, "error",
                    "Provide startTime+endTime (explicit) or durationMinutes (auto-schedule)"));
        }
    }
}
