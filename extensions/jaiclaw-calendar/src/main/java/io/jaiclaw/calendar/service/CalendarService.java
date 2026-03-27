package io.jaiclaw.calendar.service;

import io.jaiclaw.calendar.model.AppointmentRequest;
import io.jaiclaw.calendar.model.CalendarEvent;
import io.jaiclaw.calendar.model.CalendarInfo;
import io.jaiclaw.calendar.model.TimeSlot;
import io.jaiclaw.calendar.provider.CalendarProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Service layer for calendar operations.
 * Provides a facade over the calendar provider.
 */
public class CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);

    private final CalendarProvider calendarProvider;

    public CalendarService(CalendarProvider calendarProvider) {
        this.calendarProvider = calendarProvider;
    }

    public Mono<CalendarEvent> createEvent(CalendarEvent event) {
        log.debug("Creating event: {}", event.title());
        return calendarProvider.createEvent(event);
    }

    public Flux<CalendarEvent> listEvents(String tenantId, String calendarId, Instant startDate, Instant endDate, Integer limit) {
        log.debug("Listing events for tenant: {}, calendar: {} from {} to {}", tenantId, calendarId, startDate, endDate);
        return calendarProvider.listEvents(tenantId, calendarId, startDate, endDate, limit);
    }

    public Mono<CalendarEvent> getEvent(String tenantId, String calendarId, String eventId) {
        log.debug("Getting event: {} for tenant: {}, calendar: {}", eventId, tenantId, calendarId);
        return calendarProvider.getEvent(tenantId, calendarId, eventId);
    }

    public Mono<CalendarEvent> updateEvent(String tenantId, String calendarId, String eventId, Map<String, Object> updates) {
        log.debug("Updating event: {} for tenant: {}, calendar: {}", eventId, tenantId, calendarId);
        return calendarProvider.updateEvent(tenantId, calendarId, eventId, updates);
    }

    public Mono<Void> deleteEvent(String tenantId, String calendarId, String eventId) {
        log.debug("Deleting event: {} for tenant: {}, calendar: {}", eventId, tenantId, calendarId);
        return calendarProvider.deleteEvent(tenantId, calendarId, eventId);
    }

    public Flux<TimeSlot> getAvailableSlots(Instant startDate, Instant endDate, long durationSeconds) {
        log.debug("Finding available slots from {} to {} for {} seconds", startDate, endDate, durationSeconds);
        return calendarProvider.getAvailableSlots(startDate, endDate, durationSeconds);
    }

    public Mono<CalendarEvent> scheduleAppointment(AppointmentRequest request) {
        log.debug("Scheduling appointment: {}", request.title());
        return calendarProvider.scheduleAppointment(request);
    }

    public String getProviderName() {
        return calendarProvider.getProviderName();
    }

    public Flux<CalendarInfo> listCalendars(String tenantId) {
        log.debug("Listing calendars for tenant: {}", tenantId);
        return calendarProvider.listCalendars(tenantId);
    }

    public Mono<CalendarInfo> createCalendar(CalendarInfo calendar) {
        log.debug("Creating calendar: {} for tenant: {}", calendar.id(), calendar.tenantId());
        return calendarProvider.createCalendar(calendar);
    }
}
