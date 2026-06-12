package io.jaiclaw.kanban.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * A directed transition between two columns, triggered by an event name.
 *
 * @param from    source state (must reference a column)
 * @param to      target state (must reference a column)
 * @param event   event name that fires this transition
 * @param guards  optional named guards evaluated before the transition
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransitionDefinition(
        String from,
        String to,
        String event,
        Map<String, String> guards
) {
    public TransitionDefinition {
        if (from == null || from.isBlank())
            throw new IllegalArgumentException("TransitionDefinition.from is required");
        if (to == null || to.isBlank())
            throw new IllegalArgumentException("TransitionDefinition.to is required");
        if (event == null || event.isBlank())
            throw new IllegalArgumentException("TransitionDefinition.event is required");
        if (guards == null) guards = Map.of();
    }
}
