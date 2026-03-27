package io.jaiclaw.calendar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Duration;
import java.time.Instant;

public record TimeSlot(
        Instant startTime,
        Instant endTime,
        boolean available,
        String note
) {
    public TimeSlot(Instant startTime, Instant endTime) {
        this(startTime, endTime, true, null);
    }

    @JsonIgnore
    public long getDurationInSeconds() {
        return Duration.between(startTime, endTime).getSeconds();
    }

    public long getDurationInMinutes() {
        return Duration.between(startTime, endTime).toMinutes();
    }

    @JsonIgnore
    public boolean canAccommodate(long durationInSeconds) {
        return getDurationInSeconds() >= durationInSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Instant startTime;
        private Instant endTime;
        private boolean available = true;
        private String note;

        public Builder startTime(Instant startTime) { this.startTime = startTime; return this; }
        public Builder endTime(Instant endTime) { this.endTime = endTime; return this; }
        public Builder available(boolean available) { this.available = available; return this; }
        public Builder note(String note) { this.note = note; return this; }

        public TimeSlot build() {
            return new TimeSlot(startTime, endTime, available, note);
        }
    }
}
