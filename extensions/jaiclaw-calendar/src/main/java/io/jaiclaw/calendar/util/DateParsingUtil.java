package io.jaiclaw.calendar.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Utility class for parsing flexible date and datetime inputs.
 * Supports ISO 8601 date-only (e.g., "2024-01-15") and full datetime (e.g., "2024-01-15T10:00:00Z").
 */
public final class DateParsingUtil {

    private DateParsingUtil() {}

    public static Instant parseFlexibleDate(String dateString, boolean isStartDate) {
        if (dateString == null || dateString.isBlank()) {
            throw new IllegalArgumentException("Date string cannot be null or blank");
        }
        try {
            return Instant.parse(dateString);
        } catch (DateTimeParseException e) {
            try {
                LocalDate localDate = LocalDate.parse(dateString);
                if (isStartDate) {
                    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
                } else {
                    return localDate.atTime(23, 59, 59, 999_999_999)
                            .atZone(ZoneOffset.UTC)
                            .toInstant();
                }
            } catch (DateTimeParseException ex) {
                throw new DateTimeParseException(
                        "Invalid date format. Expected ISO 8601 date (e.g., 2024-01-15) or datetime (e.g., 2024-01-15T10:00:00Z)",
                        dateString, 0);
            }
        }
    }

    public static Instant parseStartDate(String dateString) {
        return parseFlexibleDate(dateString, true);
    }

    public static Instant parseEndDate(String dateString) {
        return parseFlexibleDate(dateString, false);
    }
}
