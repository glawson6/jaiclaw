package io.jaiclaw.kanban.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.jaiclaw.tasks.TaskStatus;

/**
 * One column on a board.
 *
 * @param state         the kanban state string (must be unique within the board)
 * @param name          human-readable label shown in the dashboard / ASCII view
 * @param phase         coarse {@link TaskStatus} this column maps onto for
 *                      legacy interop — letting old {@code findByStatus}
 *                      consumers see kanban cards too
 * @param wipLimit      maximum cards allowed in this column; {@code null} = unlimited
 * @param terminal      whether the card has reached an end state
 * @param terminalKind  classification of the terminal outcome
 * @param processor     optional column processor (Phase 3 wires this)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ColumnDefinition(
        String state,
        String name,
        TaskStatus phase,
        Integer wipLimit,
        boolean terminal,
        TerminalKind terminalKind,
        ProcessorDefinition processor
) {
    public ColumnDefinition {
        if (state == null || state.isBlank())
            throw new IllegalArgumentException("ColumnDefinition.state is required");
        if (name == null || name.isBlank()) name = state;
        if (phase == null) phase = terminal ? mapTerminalPhase(terminalKind) : TaskStatus.QUEUED;
    }

    private static TaskStatus mapTerminalPhase(TerminalKind kind) {
        if (kind == null) return TaskStatus.SUCCEEDED;
        return switch (kind) {
            case SUCCESS -> TaskStatus.SUCCEEDED;
            case FAILURE -> TaskStatus.FAILED;
            case CANCELLED -> TaskStatus.CANCELLED;
        };
    }
}
