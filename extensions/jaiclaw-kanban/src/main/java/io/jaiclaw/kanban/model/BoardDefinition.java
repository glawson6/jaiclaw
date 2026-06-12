package io.jaiclaw.kanban.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Optional;

/**
 * A kanban board: id, named columns, allowed transitions, and an initial
 * state. Boards are loaded from YAML or registered programmatically.
 *
 * @param id            unique board id
 * @param name          human-readable board name
 * @param tenantIds     tenants this board is visible to; empty = all tenants
 *                      (same convention as {@code PipelineDefinition})
 * @param initialState  state new cards enter
 * @param columns       columns in display order
 * @param transitions   allowed event-driven transitions between columns
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BoardDefinition(
        String id,
        String name,
        List<String> tenantIds,
        String initialState,
        List<ColumnDefinition> columns,
        List<TransitionDefinition> transitions
) {
    public BoardDefinition {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("BoardDefinition.id is required");
        if (name == null || name.isBlank()) name = id;
        if (tenantIds == null) tenantIds = List.of();
        else tenantIds = List.copyOf(tenantIds);
        if (columns == null || columns.isEmpty())
            throw new IllegalArgumentException("BoardDefinition.columns is required");
        columns = List.copyOf(columns);
        if (transitions == null) transitions = List.of();
        else transitions = List.copyOf(transitions);
        if (initialState == null || initialState.isBlank()) {
            initialState = columns.get(0).state();
        }
    }

    public Optional<ColumnDefinition> column(String state) {
        return columns.stream().filter(c -> c.state().equals(state)).findFirst();
    }

    public boolean isVisibleTo(String tenantId) {
        return tenantIds.isEmpty() || tenantIds.contains(tenantId);
    }
}
