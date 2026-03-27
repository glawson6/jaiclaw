package io.jaiclaw.calendar.util;

import io.jaiclaw.calendar.config.CalendarProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Validator for calendar event business rules.
 */
public class CalendarEventValidator {

    private static final Logger log = LoggerFactory.getLogger(CalendarEventValidator.class);

    private final CalendarProperties properties;

    public CalendarEventValidator(CalendarProperties properties) {
        this.properties = properties;
    }

    public Optional<String> validateEventTiming(Instant startTime, Instant endTime) {
        Instant now = Instant.now();

        if (startTime.isBefore(now)) {
            String error = String.format(
                    "Cannot create events in the past. Current UTC time: %s, Requested start time: %s",
                    now, startTime);
            log.warn("Event timing validation failed: {}", error);
            return Optional.of(error);
        }

        if (endTime.isBefore(startTime)) {
            String error = String.format("End time must be after start time. Start: %s, End: %s",
                    startTime, endTime);
            log.warn("Event timing validation failed: {}", error);
            return Optional.of(error);
        }

        long durationMinutes = Duration.between(startTime, endTime).toMinutes();
        int minimumDurationMinutes = properties.minimumEventDurationMinutes();
        if (durationMinutes < minimumDurationMinutes) {
            String error = String.format(
                    "Event duration must be at least %d minutes. Current duration: %d minutes",
                    minimumDurationMinutes, durationMinutes);
            log.warn("Event timing validation failed: {}", error);
            return Optional.of(error);
        }

        return Optional.empty();
    }

    public Optional<String> validateTimeRange(Instant startTime, Instant endTime) {
        if (endTime.isBefore(startTime)) {
            String error = String.format("End time must be after start time. Start: %s, End: %s",
                    startTime, endTime);
            return Optional.of(error);
        }

        long durationMinutes = Duration.between(startTime, endTime).toMinutes();
        int minimumDurationMinutes = properties.minimumEventDurationMinutes();
        if (durationMinutes < minimumDurationMinutes) {
            String error = String.format(
                    "Event duration must be at least %d minutes. Current duration: %d minutes",
                    minimumDurationMinutes, durationMinutes);
            return Optional.of(error);
        }

        return Optional.empty();
    }
}
