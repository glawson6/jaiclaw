package io.jaiclaw.kanban.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.jaiclaw.tasks.TaskRecord;

import java.time.Instant;
import java.util.List;

/**
 * Dashboard projection of a card: the subset of {@link TaskRecord} that
 * matters in a board view, plus the events currently available from this
 * card's state.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardView(
        String id,
        String name,
        String description,
        String boardId,
        String state,
        String assignee,
        long version,
        int orderIndex,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        List<String> allowedEvents
) {
    public CardView {
        if (allowedEvents == null) allowedEvents = List.of();
        else allowedEvents = List.copyOf(allowedEvents);
    }

    public static CardView from(TaskRecord task, List<String> allowedEvents) {
        return new CardView(
                task.id(), task.name(), task.description(),
                task.boardId(), task.state(), task.assignee(),
                task.version(), task.orderIndex(),
                task.createdAt(), task.startedAt(), task.completedAt(),
                allowedEvents);
    }
}
