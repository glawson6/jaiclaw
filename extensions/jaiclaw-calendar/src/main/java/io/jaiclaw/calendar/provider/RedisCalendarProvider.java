package io.jaiclaw.calendar.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.calendar.model.*;
import io.jaiclaw.calendar.util.CalendarEventValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis-based implementation of CalendarProvider using Spring Data Redis Reactive.
 * Events are stored in Redis with JSON serialization for persistent, multi-tenant calendar storage.
 * <p>
 * Key Structure:
 * <ul>
 *   <li>{@code tenant:{tenantId}:calendar:{calendarId}:event:{eventId}} → CalendarEvent JSON</li>
 *   <li>{@code tenant:{tenantId}:calendar:{calendarId}:events:all} → Set of event IDs</li>
 *   <li>{@code tenant:{tenantId}:calendar:{calendarId}:events:by-time} → ZSet (eventId:start/end → epoch)</li>
 *   <li>{@code tenant:{tenantId}:calendars} → Set of calendar IDs</li>
 *   <li>{@code tenant:{tenantId}:calendar:{calendarId}:metadata} → CalendarInfo JSON</li>
 *   <li>{@code tenant:all} → Set of tenant IDs</li>
 *   <li>{@code event:index:{eventId}} → JSON {tenantId, calendarId}</li>
 * </ul>
 */
public class RedisCalendarProvider implements CalendarProvider {

    private static final Logger log = LoggerFactory.getLogger(RedisCalendarProvider.class);

    // Key patterns
    private static final String TENANT_ALL_KEY = "tenant:all";
    private static final String TENANT_CALENDARS_PATTERN = "tenant:%s:calendars";
    private static final String CALENDAR_METADATA_PATTERN = "tenant:%s:calendar:%s:metadata";
    private static final String EVENT_KEY_PATTERN = "tenant:%s:calendar:%s:event:%s";
    private static final String ALL_EVENTS_KEY_PATTERN = "tenant:%s:calendar:%s:events:all";
    private static final String EVENTS_BY_TIME_KEY_PATTERN = "tenant:%s:calendar:%s:events:by-time";
    private static final String EVENT_INDEX_PATTERN = "event:index:%s";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CalendarEventValidator eventValidator;

    public RedisCalendarProvider(ReactiveStringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 CalendarEventValidator eventValidator) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.eventValidator = eventValidator;
        log.info("RedisCalendarProvider initialized");
    }

    @Override
    public Mono<CalendarEvent> createEvent(CalendarEvent event) {
        Optional<String> validationError = eventValidator.validateEventTiming(event.startTime(), event.endTime());
        if (validationError.isPresent()) {
            return Mono.error(new IllegalArgumentException(validationError.get()));
        }

        return Mono.fromCallable(() -> {
                    String id = (event.id() == null || event.id().isEmpty())
                            ? UUID.randomUUID().toString() : event.id();
                    Instant now = Instant.now();
                    return CalendarEvent.builder()
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
                })
                .publishOn(Schedulers.boundedElastic())
                .flatMap(this::saveEvent)
                .doOnSuccess(saved -> log.info("Created event in Redis: {} - {}", saved.id(), saved.title()))
                .doOnError(error -> log.error("Failed to create event in Redis", error));
    }

    @Override
    public Flux<CalendarEvent> listEvents(String tenantId, String calendarId, Instant startDate, Instant endDate, Integer limit) {
        if (calendarId != null) {
            return listEventsForCalendar(tenantId, calendarId, startDate, endDate, limit);
        }
        return listEventsForTenant(tenantId, startDate, endDate, limit);
    }

    private Flux<CalendarEvent> listEventsForTenant(String tenantId, Instant startDate, Instant endDate, Integer limit) {
        String calendarsKey = String.format(TENANT_CALENDARS_PATTERN, tenantId);
        Range<Double> range = Range.closed(
                (double) startDate.getEpochSecond(),
                (double) endDate.getEpochSecond());

        Flux<CalendarEvent> eventFlux = redisTemplate.opsForSet()
                .members(calendarsKey)
                .flatMap(calId -> {
                    String eventsByTimeKey = String.format(EVENTS_BY_TIME_KEY_PATTERN, tenantId, calId);
                    return redisTemplate.opsForZSet()
                            .rangeByScore(eventsByTimeKey, range)
                            .map(RedisCalendarProvider::extractEventId)
                            .distinct()
                            .flatMap(eventId -> getEvent(tenantId, calId, eventId));
                })
                .filter(event -> event.overlapsWith(startDate, endDate))
                .filter(event -> event.status() != EventStatus.CANCELLED)
                .sort(Comparator.comparing(CalendarEvent::startTime))
                .doOnComplete(() -> log.debug("Listed events for tenant: {} from {} to {}", tenantId, startDate, endDate));

        if (limit != null && limit > 0) {
            return eventFlux.take(limit);
        }
        return eventFlux;
    }

    private Flux<CalendarEvent> listEventsForCalendar(String tenantId, String calendarId, Instant startDate, Instant endDate, Integer limit) {
        String eventsByTimeKey = String.format(EVENTS_BY_TIME_KEY_PATTERN, tenantId, calendarId);
        Range<Double> range = Range.closed(
                (double) startDate.getEpochSecond(),
                (double) endDate.getEpochSecond());

        Flux<CalendarEvent> eventFlux = redisTemplate.opsForZSet()
                .rangeByScore(eventsByTimeKey, range)
                .map(RedisCalendarProvider::extractEventId)
                .distinct()
                .flatMap(eventId -> getEvent(tenantId, calendarId, eventId))
                .filter(event -> event.overlapsWith(startDate, endDate))
                .filter(event -> event.status() != EventStatus.CANCELLED)
                .sort(Comparator.comparing(CalendarEvent::startTime))
                .doOnComplete(() -> log.debug("Listed events for tenant: {}, calendar: {} from {} to {}",
                        tenantId, calendarId, startDate, endDate));

        if (limit != null && limit > 0) {
            return eventFlux.take(limit);
        }
        return eventFlux;
    }

    @Override
    public Mono<CalendarEvent> getEvent(String tenantId, String calendarId, String eventId) {
        String key = String.format(EVENT_KEY_PATTERN, tenantId, calendarId, eventId);
        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(json -> deserialize(json, CalendarEvent.class))
                .doOnNext(event -> log.debug("Retrieved event: {} - {}", eventId, event.title()))
                .doOnError(error -> log.error("Failed to get event: {} for tenant: {}, calendar: {}",
                        eventId, tenantId, calendarId, error));
    }

    @Override
    public Mono<CalendarEvent> updateEvent(String tenantId, String calendarId, String eventId, Map<String, Object> updates) {
        return getEvent(tenantId, calendarId, eventId)
                .switchIfEmpty(Mono.error(new RuntimeException("Event not found: " + eventId)))
                .flatMap(oldEvent -> {
                    Instant oldStartTime = oldEvent.startTime();
                    Instant oldEndTime = oldEvent.endTime();

                    CalendarEvent updated = applyUpdates(oldEvent, updates);

                    Optional<String> validationError = eventValidator.validateEventTiming(
                            updated.startTime(), updated.endTime());
                    if (validationError.isPresent()) {
                        return Mono.error(new IllegalArgumentException(validationError.get()));
                    }

                    boolean timesChanged = !oldStartTime.equals(updated.startTime()) ||
                            !oldEndTime.equals(updated.endTime());

                    if (timesChanged) {
                        String eventsByTimeKey = String.format(EVENTS_BY_TIME_KEY_PATTERN, tenantId, calendarId);
                        String oldStartKey = eventId + ":start";
                        String oldEndKey = eventId + ":end";

                        return Mono.when(
                                        redisTemplate.opsForZSet().remove(eventsByTimeKey, oldStartKey),
                                        redisTemplate.opsForZSet().remove(eventsByTimeKey, oldEndKey)
                                )
                                .then(saveEvent(updated))
                                .doOnSuccess(v -> log.debug("Updated zset entries for event with new times: {}", eventId));
                    } else {
                        return saveEvent(updated);
                    }
                })
                .doOnSuccess(event -> log.info("Updated event for tenant: {}, calendar: {}, event: {} - {}",
                        tenantId, calendarId, eventId, event.title()));
    }

    @Override
    public Mono<Void> deleteEvent(String tenantId, String calendarId, String eventId) {
        String eventKey = String.format(EVENT_KEY_PATTERN, tenantId, calendarId, eventId);
        String allEventsKey = String.format(ALL_EVENTS_KEY_PATTERN, tenantId, calendarId);
        String eventsByTimeKey = String.format(EVENTS_BY_TIME_KEY_PATTERN, tenantId, calendarId);
        String eventIndexKey = String.format(EVENT_INDEX_PATTERN, eventId);
        String startKey = eventId + ":start";
        String endKey = eventId + ":end";

        return getEvent(tenantId, calendarId, eventId)
                .flatMap(event -> Mono.when(
                                redisTemplate.opsForValue().delete(eventKey),
                                redisTemplate.opsForSet().remove(allEventsKey, eventId),
                                redisTemplate.opsForZSet().remove(eventsByTimeKey, startKey),
                                redisTemplate.opsForZSet().remove(eventsByTimeKey, endKey),
                                redisTemplate.opsForValue().delete(eventIndexKey)
                        )
                        .doOnSuccess(v -> log.info("Deleted event for tenant: {}, calendar: {}, event: {} - {}",
                                tenantId, calendarId, eventId, event.title())))
                .then()
                .doOnError(error -> log.error("Failed to delete event: {} for tenant: {}, calendar: {}",
                        eventId, tenantId, calendarId, error));
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

                    log.debug("Found {} available slots from {} to {}", availableSlots.size(), startDate, endDate);
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
                .switchIfEmpty(Mono.error(new RuntimeException("No available slots found for the appointment")))
                .doOnSuccess(event -> log.info("Scheduled appointment: {} at {}", event.title(), event.startTime()));
    }

    @Override
    public String getProviderName() {
        return "redis";
    }

    @Override
    public Flux<CalendarInfo> listCalendars(String tenantId) {
        String calendarsKey = String.format(TENANT_CALENDARS_PATTERN, tenantId);

        return redisTemplate.opsForSet()
                .members(calendarsKey)
                .flatMap(calendarId -> getCalendarMetadata(tenantId, calendarId))
                .doOnComplete(() -> log.debug("Listed calendars for tenant: {}", tenantId))
                .doOnError(error -> log.error("Failed to list calendars for tenant: {}", tenantId, error));
    }

    @Override
    public Mono<CalendarInfo> createCalendar(CalendarInfo calendar) {
        String tenantId = calendar.tenantId();
        String calendarId = calendar.id();
        String calendarsKey = String.format(TENANT_CALENDARS_PATTERN, tenantId);
        String metadataKey = String.format(CALENDAR_METADATA_PATTERN, tenantId, calendarId);

        return redisTemplate.opsForSet()
                .isMember(calendarsKey, calendarId)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new RuntimeException(
                                "Calendar with ID '" + calendarId + "' already exists for tenant '" + tenantId + "'"));
                    }

                    Instant now = Instant.now();
                    CalendarInfo stored = CalendarInfo.builder()
                            .id(calendarId)
                            .tenantId(tenantId)
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

                    return serialize(stored)
                            .flatMap(json -> Mono.when(
                                            redisTemplate.opsForSet().add(TENANT_ALL_KEY, tenantId),
                                            redisTemplate.opsForSet().add(calendarsKey, calendarId),
                                            redisTemplate.opsForValue().set(metadataKey, json)
                                    )
                                    .thenReturn(stored));
                })
                .doOnSuccess(cal -> log.info("Created calendar: {} for tenant: {}", calendarId, tenantId))
                .doOnError(error -> log.error("Failed to create calendar: {} for tenant: {}", calendarId, tenantId, error));
    }

    // --- Internal helpers ---

    private Mono<CalendarInfo> getCalendarMetadata(String tenantId, String calendarId) {
        String metadataKey = String.format(CALENDAR_METADATA_PATTERN, tenantId, calendarId);

        return redisTemplate.opsForValue()
                .get(metadataKey)
                .flatMap(json -> deserialize(json, CalendarInfo.class))
                .switchIfEmpty(Mono.just(CalendarInfo.builder()
                        .id(calendarId)
                        .tenantId(tenantId)
                        .name(calendarId)
                        .description("Calendar: " + calendarId)
                        .color("#667eea")
                        .visible(true)
                        .isDefault("default".equals(calendarId))
                        .timezone("UTC")
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()));
    }

    private Mono<CalendarEvent> saveEvent(CalendarEvent event) {
        String tenantId = event.tenantId();
        String calendarId = event.calendarId();
        String eventId = event.id();

        String eventKey = String.format(EVENT_KEY_PATTERN, tenantId, calendarId, eventId);
        String allEventsKey = String.format(ALL_EVENTS_KEY_PATTERN, tenantId, calendarId);
        String eventsByTimeKey = String.format(EVENTS_BY_TIME_KEY_PATTERN, tenantId, calendarId);
        String calendarsKey = String.format(TENANT_CALENDARS_PATTERN, tenantId);
        String eventIndexKey = String.format(EVENT_INDEX_PATTERN, eventId);

        String startKey = eventId + ":start";
        String endKey = eventId + ":end";
        String indexData = String.format("{\"tenantId\":\"%s\",\"calendarId\":\"%s\"}", tenantId, calendarId);

        return serialize(event)
                .flatMap(json -> Mono.when(
                                redisTemplate.opsForValue().set(eventKey, json),
                                redisTemplate.opsForSet().add(TENANT_ALL_KEY, tenantId),
                                redisTemplate.opsForSet().add(calendarsKey, calendarId),
                                redisTemplate.opsForSet().add(allEventsKey, eventId),
                                redisTemplate.opsForZSet().add(eventsByTimeKey, startKey, event.startTime().getEpochSecond()),
                                redisTemplate.opsForZSet().add(eventsByTimeKey, endKey, event.endTime().getEpochSecond()),
                                redisTemplate.opsForValue().set(eventIndexKey, indexData)
                        )
                        .thenReturn(event))
                .doOnError(error -> log.error("Failed to save event to Redis: {}", eventId, error));
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
                case "email" -> builder.email((String) value);
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

    private <T> Mono<T> deserialize(String json, Class<T> type) {
        try {
            return Mono.just(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize {}: {}", type.getSimpleName(), e.getMessage());
            return Mono.error(new RuntimeException("Failed to deserialize " + type.getSimpleName(), e));
        }
    }

    private Mono<String> serialize(Object value) {
        try {
            return Mono.just(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Failed to serialize " + value.getClass().getSimpleName(), e));
        }
    }

    private static String extractEventId(String zsetKey) {
        int colonIndex = zsetKey.lastIndexOf(":");
        return colonIndex > 0 ? zsetKey.substring(0, colonIndex) : zsetKey;
    }

    private Instant parseInstant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof String s) return Instant.parse(s);
        if (value instanceof Long l) return Instant.ofEpochSecond(l);
        throw new IllegalArgumentException("Cannot parse Instant from: " + value);
    }
}
