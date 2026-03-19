package io.jclaw.cron;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;

/**
 * Parses standard cron expressions and computes next fire times.
 * Supports 5-field cron: minute hour day-of-month month day-of-week.
 */
public class CronScheduleComputer {

    public Optional<Instant> nextFireTime(String cronExpression, String timezone) {
        return nextFireTime(cronExpression, timezone, Instant.now());
    }

    public Optional<Instant> nextFireTime(String cronExpression, String timezone, Instant after) {
        try {
            ZoneId zone = ZoneId.of(timezone);
            ZonedDateTime now = after.atZone(zone).truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
            int[] fields = parseCron(cronExpression);

            for (int i = 0; i < 525960; i++) {
                ZonedDateTime candidate = now.plusMinutes(i);
                if (matches(candidate, fields)) {
                    return Optional.of(candidate.toInstant());
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    boolean matches(ZonedDateTime dt, int[] fields) {
        return matchField(dt.getMinute(), fields[0])
                && matchField(dt.getHour(), fields[1])
                && matchField(dt.getDayOfMonth(), fields[2])
                && matchField(dt.getMonthValue(), fields[3])
                && matchField(dt.getDayOfWeek().getValue() % 7, fields[4]);
    }

    int[] parseCron(String expression) {
        String[] parts = expression.trim().split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid cron expression: expected 5 fields, got " + parts.length);
        }
        int[] fields = new int[5];
        for (int i = 0; i < 5; i++) {
            fields[i] = parts[i].equals("*") ? -1 : Integer.parseInt(parts[i]);
        }
        return fields;
    }

    private boolean matchField(int actual, int expected) {
        return expected == -1 || expected == actual;
    }
}
