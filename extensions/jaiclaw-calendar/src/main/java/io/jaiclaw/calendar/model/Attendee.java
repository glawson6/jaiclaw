package io.jaiclaw.calendar.model;

public record Attendee(
        String name,
        String email,
        AttendeeStatus status,
        boolean optional,
        String responseMessage
) {
    public Attendee(String name, String email) {
        this(name, email, AttendeeStatus.PENDING, false, null);
    }

    public Attendee(String name, String email, AttendeeStatus status) {
        this(name, email, status, false, null);
    }
}
