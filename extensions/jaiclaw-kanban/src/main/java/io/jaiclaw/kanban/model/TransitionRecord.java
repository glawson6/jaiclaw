package io.jaiclaw.kanban.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Immutable record of one accepted transition. Same shape carried on the
 * Spring {@code TaskStateChanged} application event and persisted by the
 * Phase 4 transition journal.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransitionRecord(
        String taskId,
        String boardId,
        String fromState,
        String toState,
        String event,
        String actor,
        String tenantId,
        Instant timestamp
) {
    public TransitionRecord {
        if (timestamp == null) timestamp = Instant.now();
    }
}
