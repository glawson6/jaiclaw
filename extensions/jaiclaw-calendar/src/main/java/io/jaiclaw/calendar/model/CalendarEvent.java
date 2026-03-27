package io.jaiclaw.calendar.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CalendarEvent(
        String id,
        String tenantId,
        String calendarId,
        String email,
        String title,
        String description,
        String timezone,
        Instant startTime,
        Instant endTime,
        String location,
        List<Attendee> attendees,
        String organizer,
        EventStatus status,
        boolean allDay,
        String recurrenceRule,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean overlapsWith(Instant start, Instant end) {
        return !this.endTime.isBefore(start) && !this.startTime.isAfter(end);
    }

    public long getDurationInSeconds() {
        return endTime.getEpochSecond() - startTime.getEpochSecond();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String tenantId;
        private String calendarId;
        private String email;
        private String title;
        private String description = "";
        private String timezone = "America/New_York";
        private Instant startTime;
        private Instant endTime;
        private String location = "";
        private List<Attendee> attendees = List.of();
        private String organizer;
        private EventStatus status = EventStatus.CONFIRMED;
        private boolean allDay = false;
        private String recurrenceRule;
        private Map<String, Object> metadata = Map.of();
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder calendarId(String calendarId) { this.calendarId = calendarId; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder timezone(String timezone) { this.timezone = timezone; return this; }
        public Builder startTime(Instant startTime) { this.startTime = startTime; return this; }
        public Builder endTime(Instant endTime) { this.endTime = endTime; return this; }
        public Builder location(String location) { this.location = location; return this; }
        public Builder attendees(List<Attendee> attendees) { this.attendees = attendees; return this; }
        public Builder organizer(String organizer) { this.organizer = organizer; return this; }
        public Builder status(EventStatus status) { this.status = status; return this; }
        public Builder allDay(boolean allDay) { this.allDay = allDay; return this; }
        public Builder recurrenceRule(String recurrenceRule) { this.recurrenceRule = recurrenceRule; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public CalendarEvent build() {
            return new CalendarEvent(id, tenantId, calendarId, email, title, description,
                    timezone, startTime, endTime, location, attendees, organizer, status,
                    allDay, recurrenceRule, metadata, createdAt, updatedAt);
        }
    }
}
