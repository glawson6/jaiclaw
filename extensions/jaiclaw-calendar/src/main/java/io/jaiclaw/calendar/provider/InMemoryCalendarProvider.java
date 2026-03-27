package io.jaiclaw.calendar.provider;

import io.jaiclaw.calendar.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of CalendarProvider for development and testing.
 */
public class InMemoryCalendarProvider implements CalendarProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemoryCalendarProvider.class);

    private final ConcurrentHashMap<String, CalendarEvent> events = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CalendarInfo> calendars = new ConcurrentHashMap<>();

    public void initialize() {
        log.info("Initializing in-memory calendar with sample events for next 2 months");
        Instant now = Instant.now();
        Instant twoMonthsLater = now.plus(60, ChronoUnit.DAYS);
        List<CalendarEvent> sampleEvents = createSampleEvents(now, twoMonthsLater);
        sampleEvents.forEach(event -> events.put(event.id(), event));
        log.info("Initialized {} sample events in calendar", sampleEvents.size());
    }

    private List<CalendarEvent> createSampleEvents(Instant start, Instant end) {
        List<CalendarEvent> sampleEvents = new ArrayList<>();
        Instant current = start;

        String[] meetingTypes = {
            "Team Standup", "Client Meeting", "Project Review", "1-on-1 Sync",
            "Planning Session", "Sprint Retrospective", "Architecture Review",
            "Product Demo", "Training Session", "All Hands Meeting"
        };
        String[] locations = {
            "Conference Room A", "Conference Room B", "Virtual - Zoom",
            "Virtual - Teams", "Office - 3rd Floor", "External - Client Site"
        };

        int eventIndex = 0;
        Instant now = Instant.now();

        while (current.isBefore(end) && eventIndex < meetingTypes.length * 3) {
            LocalDateTime localDateTime = LocalDateTime.ofInstant(current, ZoneOffset.UTC);
            DayOfWeek dayOfWeek = localDateTime.getDayOfWeek();

            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                current = current.plus(1, ChronoUnit.DAYS);
                continue;
            }

            LocalDateTime businessHourStart = localDateTime.withHour(9).withMinute(0).withSecond(0).withNano(0);
            int eventsPerDay = 1 + (eventIndex % 3);

            for (int i = 0; i < eventsPerDay && eventIndex < meetingTypes.length * 3; i++) {
                int hourOffset = i * 3;
                if (hourOffset + 9 > 17) break;

                LocalDateTime eventStart = businessHourStart.plusHours(hourOffset);
                int durationMinutes = switch (eventIndex % 3) {
                    case 0 -> 30;
                    case 1 -> 45;
                    default -> 60;
                };
                LocalDateTime eventEnd = eventStart.plusMinutes(durationMinutes);
                String meetingType = meetingTypes[eventIndex % meetingTypes.length];
                String location = locations[eventIndex % locations.length];

                CalendarEvent event = CalendarEvent.builder()
                        .id(UUID.randomUUID().toString())
                        .title(meetingType)
                        .description("Sample event for demonstration and testing")
                        .startTime(eventStart.toInstant(ZoneOffset.UTC))
                        .endTime(eventEnd.toInstant(ZoneOffset.UTC))
                        .location(location)
                        .organizer("calendar-system@taptech.com")
                        .status(EventStatus.CONFIRMED)
                        .allDay(false)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

                sampleEvents.add(event);
                eventIndex++;
            }
            current = current.plus(1, ChronoUnit.DAYS);
        }
        return sampleEvents;
    }

    @Override
    public Mono<CalendarEvent> createEvent(CalendarEvent event) {
        return Mono.fromCallable(() -> {
            String id = (event.id() == null || event.id().isEmpty())
                    ? UUID.randomUUID().toString() : event.id();
            Instant now = Instant.now();
            CalendarEvent stored = CalendarEvent.builder()
                    .id(id)
                    .tenantId(event.tenantId())
                    .calendarId(event.calendarId())
                    .email(event.email())
                    .title(event.title())
                    .description(event.description())
                    .timezone(event.timezone())
                    .startTime(event.startTime())
                    .endTime(event.endTime())
                    .location(event.location())
                    .attendees(event.attendees())
                    .organizer(event.organizer())
                    .status(event.status())
                    .allDay(event.allDay())
                    .recurrenceRule(event.recurrenceRule())
                    .metadata(event.metadata())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            events.put(id, stored);
            log.info("Created event: {} - {}", id, stored.title());
            return stored;
        });
    }

    @Override
    public Flux<CalendarEvent> listEvents(String tenantId, String calendarId, Instant startDate, Instant endDate, Integer limit) {
        Flux<CalendarEvent> eventFlux = Flux.fromIterable(events.values())
                .filter(event -> event.overlapsWith(startDate, endDate))
                .filter(event -> event.status() != EventStatus.CANCELLED)
                .filter(event -> tenantId == null || tenantId.equals(event.tenantId()) || event.tenantId() == null)
                .filter(event -> calendarId == null || calendarId.equals(event.calendarId()) || event.calendarId() == null)
                .sort(Comparator.comparing(CalendarEvent::startTime));
        if (limit != null && limit > 0) {
            return eventFlux.take(limit);
        }
        return eventFlux;
    }

    @Override
    public Mono<CalendarEvent> getEvent(String tenantId, String calendarId, String eventId) {
        return Mono.justOrEmpty(events.get(eventId));
    }

    @Override
    public Mono<CalendarEvent> updateEvent(String tenantId, String calendarId, String eventId, Map<String, Object> updates) {
        return getEvent(tenantId, calendarId, eventId)
                .switchIfEmpty(Mono.error(new RuntimeException("Event not found: " + eventId)))
                .flatMap(event -> Mono.fromCallable(() -> {
                    CalendarEvent updated = applyUpdates(event, updates);
                    events.put(eventId, updated);
                    log.info("Updated event: {} - {}", eventId, updated.title());
                    return updated;
                }));
    }

    @Override
    public Mono<Void> deleteEvent(String tenantId, String calendarId, String eventId) {
        return Mono.fromRunnable(() -> {
            CalendarEvent removed = events.remove(eventId);
            if (removed != null) {
                log.info("Deleted event: {} - {}", eventId, removed.title());
            }
        });
    }

    @Override
    public Flux<TimeSlot> getAvailableSlots(Instant startDate, Instant endDate, long durationSeconds) {
        return listEvents(null, null, startDate, endDate, null)
                .collectList()
                .flatMapMany(busyEvents -> {
                    List<TimeSlot> availableSlots = new ArrayList<>();
                    Instant currentTime = startDate;

                    List<CalendarEvent> sortedEvents = busyEvents.stream()
                            .sorted(Comparator.comparing(CalendarEvent::startTime))
                            .collect(Collectors.toList());

                    for (CalendarEvent event : sortedEvents) {
                        if (currentTime.isBefore(event.startTime())) {
                            long gapDuration = Duration.between(currentTime, event.startTime()).getSeconds();
                            if (gapDuration >= durationSeconds) {
                                availableSlots.add(new TimeSlot(currentTime, event.startTime()));
                            }
                        }
                        if (event.endTime().isAfter(currentTime)) {
                            currentTime = event.endTime();
                        }
                    }

                    if (currentTime.isBefore(endDate)) {
                        long remainingDuration = Duration.between(currentTime, endDate).getSeconds();
                        if (remainingDuration >= durationSeconds) {
                            availableSlots.add(new TimeSlot(currentTime, endDate));
                        }
                    }

                    return Flux.fromIterable(availableSlots);
                });
    }

    @Override
    public Mono<CalendarEvent> scheduleAppointment(AppointmentRequest request) {
        if (!request.isValid()) {
            return Mono.error(new IllegalArgumentException("Invalid appointment request"));
        }

        if (request.preferredStartTime() != null && request.preferredEndTime() != null) {
            return createEvent(buildEventFromRequest(request, request.preferredStartTime(), request.preferredEndTime()));
        }

        Instant searchStart = request.earliestTime() != null ? request.earliestTime() : Instant.now();
        Instant searchEnd = request.latestTime() != null ? request.latestTime() : Instant.now().plus(Duration.ofDays(30));
        long duration = request.durationInSeconds() != null ? request.durationInSeconds() : 3600;

        return getAvailableSlots(searchStart, searchEnd, duration)
                .next()
                .flatMap(slot -> {
                    Instant appointmentStart = slot.startTime();
                    Instant appointmentEnd = appointmentStart.plusSeconds(duration);
                    return createEvent(buildEventFromRequest(request, appointmentStart, appointmentEnd));
                })
                .switchIfEmpty(Mono.error(new RuntimeException("No available slots found for the appointment")));
    }

    @Override
    public String getProviderName() {
        return "in-memory";
    }

    @Override
    public Flux<CalendarInfo> listCalendars(String tenantId) {
        return Flux.fromIterable(calendars.values())
                .filter(cal -> tenantId == null || tenantId.equals(cal.tenantId()));
    }

    @Override
    public Mono<CalendarInfo> createCalendar(CalendarInfo calendar) {
        return Mono.fromCallable(() -> {
            String key = calendar.tenantId() + ":" + calendar.id();
            if (calendars.containsKey(key)) {
                throw new RuntimeException("Calendar with ID '" + calendar.id() + "' already exists for tenant '" + calendar.tenantId() + "'");
            }
            Instant now = Instant.now();
            CalendarInfo stored = CalendarInfo.builder()
                    .id(calendar.id())
                    .tenantId(calendar.tenantId())
                    .name(calendar.name())
                    .description(calendar.description())
                    .color(calendar.color())
                    .visible(calendar.visible())
                    .isDefault(calendar.isDefault())
                    .timezone(calendar.timezone())
                    .metadata(calendar.metadata())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            calendars.put(key, stored);
            log.info("Created calendar: {} for tenant: {}", stored.id(), stored.tenantId());
            return stored;
        });
    }

    public void clear() {
        events.clear();
        calendars.clear();
    }

    public int getEventCount() {
        return events.size();
    }

    private CalendarEvent buildEventFromRequest(AppointmentRequest request, Instant startTime, Instant endTime) {
        return CalendarEvent.builder()
                .title(request.title())
                .description(request.description())
                .email(request.email())
                .startTime(startTime)
                .endTime(endTime)
                .location(request.location())
                .organizer(request.organizer())
                .attendees(request.attendees())
                .status(EventStatus.CONFIRMED)
                .build();
    }

    private CalendarEvent applyUpdates(CalendarEvent event, Map<String, Object> updates) {
        var builder = CalendarEvent.builder()
                .id(event.id())
                .tenantId(event.tenantId())
                .calendarId(event.calendarId())
                .email(event.email())
                .title(event.title())
                .description(event.description())
                .timezone(event.timezone())
                .startTime(event.startTime())
                .endTime(event.endTime())
                .location(event.location())
                .attendees(event.attendees())
                .organizer(event.organizer())
                .status(event.status())
                .allDay(event.allDay())
                .recurrenceRule(event.recurrenceRule())
                .metadata(event.metadata())
                .createdAt(event.createdAt())
                .updatedAt(Instant.now());

        updates.forEach((key, value) -> {
            switch (key) {
                case "title" -> builder.title((String) value);
                case "description" -> builder.description((String) value);
                case "startTime" -> builder.startTime(parseInstant(value));
                case "endTime" -> builder.endTime(parseInstant(value));
                case "location" -> builder.location((String) value);
                case "status" -> builder.status(EventStatus.valueOf((String) value));
                case "allDay" -> builder.allDay((Boolean) value);
                case "organizer" -> builder.organizer((String) value);
                default -> log.warn("Unknown update field: {}", key);
            }
        });
        return builder.build();
    }

    private Instant parseInstant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof String s) return Instant.parse(s);
        if (value instanceof Long l) return Instant.ofEpochSecond(l);
        throw new IllegalArgumentException("Cannot parse Instant from: " + value);
    }
}
