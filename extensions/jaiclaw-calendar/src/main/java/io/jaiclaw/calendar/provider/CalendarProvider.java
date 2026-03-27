package io.jaiclaw.calendar.provider;

import io.jaiclaw.calendar.model.AppointmentRequest;
import io.jaiclaw.calendar.model.CalendarEvent;
import io.jaiclaw.calendar.model.CalendarInfo;
import io.jaiclaw.calendar.model.TimeSlot;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * SPI for calendar providers.
 * Implementations provide calendar operations for different backends
 * (in-memory, Redis, Google Calendar, etc.).
 */
public interface CalendarProvider {

    Mono<CalendarEvent> createEvent(CalendarEvent event);

    Flux<CalendarEvent> listEvents(String tenantId, String calendarId, Instant startDate, Instant endDate, Integer limit);

    default Flux<CalendarEvent> listEvents(String tenantId, Instant startDate, Instant endDate, Integer limit) {
        return listEvents(tenantId, null, startDate, endDate, limit);
    }

    Mono<CalendarEvent> getEvent(String tenantId, String calendarId, String eventId);

    Mono<CalendarEvent> updateEvent(String tenantId, String calendarId, String eventId, Map<String, Object> updates);

    Mono<Void> deleteEvent(String tenantId, String calendarId, String eventId);

    Flux<TimeSlot> getAvailableSlots(Instant startDate, Instant endDate, long durationSeconds);

    Mono<CalendarEvent> scheduleAppointment(AppointmentRequest request);

    String getProviderName();

    Flux<CalendarInfo> listCalendars(String tenantId);

    Mono<CalendarInfo> createCalendar(CalendarInfo calendar);
}
