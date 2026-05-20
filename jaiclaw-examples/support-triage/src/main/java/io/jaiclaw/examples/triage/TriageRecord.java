package io.jaiclaw.examples.triage;

import java.time.Instant;

public record TriageRecord(
        String ticketId,
        String message,
        String sentiment,
        String categories,
        String priority,
        String team,
        String decision,
        Instant timestamp
) {}
