package io.jaiclaw.calendar.model;

import java.time.Instant;
import java.util.List;

public record AppointmentRequest(
        String email,
        String title,
        String description,
        Instant preferredStartTime,
        Instant preferredEndTime,
        Long durationInSeconds,
        Instant earliestTime,
        Instant latestTime,
        List<Attendee> attendees,
        String location,
        String organizer,
        String notes,
        boolean findEarliestSlot
) {
    public boolean isValid() {
        if (title == null || title.isBlank()) return false;
        if (preferredStartTime == null && preferredEndTime == null && durationInSeconds == null) return false;
        if (preferredStartTime != null && preferredEndTime != null && !preferredEndTime.isAfter(preferredStartTime)) return false;
        if (earliestTime != null && latestTime != null && !latestTime.isAfter(earliestTime)) return false;
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String email;
        private String title;
        private String description = "";
        private Instant preferredStartTime;
        private Instant preferredEndTime;
        private Long durationInSeconds;
        private Instant earliestTime;
        private Instant latestTime;
        private List<Attendee> attendees = List.of();
        private String location = "";
        private String organizer = "";
        private String notes;
        private boolean findEarliestSlot = true;

        public Builder email(String email) { this.email = email; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder preferredStartTime(Instant preferredStartTime) { this.preferredStartTime = preferredStartTime; return this; }
        public Builder preferredEndTime(Instant preferredEndTime) { this.preferredEndTime = preferredEndTime; return this; }
        public Builder durationInSeconds(Long durationInSeconds) { this.durationInSeconds = durationInSeconds; return this; }
        public Builder earliestTime(Instant earliestTime) { this.earliestTime = earliestTime; return this; }
        public Builder latestTime(Instant latestTime) { this.latestTime = latestTime; return this; }
        public Builder attendees(List<Attendee> attendees) { this.attendees = attendees; return this; }
        public Builder location(String location) { this.location = location; return this; }
        public Builder organizer(String organizer) { this.organizer = organizer; return this; }
        public Builder notes(String notes) { this.notes = notes; return this; }
        public Builder findEarliestSlot(boolean findEarliestSlot) { this.findEarliestSlot = findEarliestSlot; return this; }

        public AppointmentRequest build() {
            return new AppointmentRequest(email, title, description, preferredStartTime,
                    preferredEndTime, durationInSeconds, earliestTime, latestTime,
                    attendees, location, organizer, notes, findEarliestSlot);
        }
    }
}
