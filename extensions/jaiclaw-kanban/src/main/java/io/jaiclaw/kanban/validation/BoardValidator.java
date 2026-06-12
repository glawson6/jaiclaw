package io.jaiclaw.kanban.validation;

import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.model.ColumnDefinition;
import io.jaiclaw.kanban.model.ProcessorDefinition;
import io.jaiclaw.kanban.model.TransitionDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Validates a list of {@link BoardDefinition}s and returns a consolidated
 * {@link BoardValidationReport}. Mirrors {@code PipelineValidator} but
 * scoped to the smaller kanban surface.
 *
 * <p>Checks performed per board:
 * <ol>
 *   <li>Duplicate state names within columns.</li>
 *   <li>{@code initialState} references a defined column.</li>
 *   <li>Every {@code transition.from} / {@code transition.to} references a
 *       defined column. "Did you mean?" suggestions on misses.</li>
 *   <li>At least one terminal column is declared.</li>
 *   <li>Every non-terminal column is reachable from {@code initialState}
 *       and can reach at least one terminal column.</li>
 *   <li>{@code restartPolicy=requeue} requires {@code idempotent=true} on
 *       the same processor (analysis §6.8).</li>
 * </ol>
 */
public class BoardValidator {

    static final int SUGGESTION_MAX_DISTANCE = 2;

    public BoardValidationReport validate(List<BoardDefinition> boards) {
        BoardValidationReport.Builder report = new BoardValidationReport.Builder();
        if (boards == null) return report.build();
        for (BoardDefinition board : boards) {
            validateBoard(board, report);
        }
        return report.build();
    }

    public BoardValidationReport validate(BoardDefinition board) {
        return validate(List.of(board));
    }

    public void validateOrThrow(List<BoardDefinition> boards) {
        BoardValidationReport report = validate(boards);
        if (report.hasErrors()) {
            throw new IllegalStateException(report.formatted());
        }
    }

    private void validateBoard(BoardDefinition board, BoardValidationReport.Builder report) {
        String id = board.id();
        Set<String> states = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (ColumnDefinition col : board.columns()) {
            if (!states.add(col.state())) {
                duplicates.add(col.state());
            }
        }
        for (String dup : duplicates) {
            report.add(new BoardValidationError(id, "columns",
                    "DUPLICATE_STATE",
                    "state '" + dup + "' is declared more than once",
                    null));
        }

        if (!states.contains(board.initialState())) {
            report.add(new BoardValidationError(id, "initialState",
                    "UNKNOWN_INITIAL_STATE",
                    "initialState '" + board.initialState() + "' is not a declared column",
                    Levenshtein.suggest(board.initialState(), states, SUGGESTION_MAX_DISTANCE)
                            .orElse(null)));
        }

        Set<String> terminals = new TreeSet<>();
        Map<String, ProcessorDefinition> processorByState = new HashMap<>();
        for (ColumnDefinition col : board.columns()) {
            if (col.terminal()) terminals.add(col.state());
            if (col.processor() != null) processorByState.put(col.state(), col.processor());
        }
        if (terminals.isEmpty()) {
            report.add(new BoardValidationError(id, "columns",
                    "NO_TERMINAL_COLUMN",
                    "board declares no terminal column — cards have nowhere to land",
                    null));
        }

        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        for (int i = 0; i < board.transitions().size(); i++) {
            TransitionDefinition t = board.transitions().get(i);
            String location = "transitions[" + i + "]";
            if (!states.contains(t.from())) {
                report.add(new BoardValidationError(id, location,
                        "UNKNOWN_FROM_STATE",
                        "transition '" + t.event() + "' from unknown state '" + t.from() + "'",
                        Levenshtein.suggest(t.from(), states, SUGGESTION_MAX_DISTANCE).orElse(null)));
                continue;
            }
            if (!states.contains(t.to())) {
                report.add(new BoardValidationError(id, location,
                        "UNKNOWN_TO_STATE",
                        "transition '" + t.event() + "' to unknown state '" + t.to() + "'",
                        Levenshtein.suggest(t.to(), states, SUGGESTION_MAX_DISTANCE).orElse(null)));
                continue;
            }
            outgoing.computeIfAbsent(t.from(), k -> new ArrayList<>()).add(t.to());
            incoming.computeIfAbsent(t.to(), k -> new ArrayList<>()).add(t.from());
        }

        Set<String> reachableForward = reachable(board.initialState(), outgoing);
        for (String state : states) {
            if (!reachableForward.contains(state)) {
                report.add(new BoardValidationError(id, "columns",
                        "UNREACHABLE_STATE",
                        "state '" + state + "' is not reachable from initialState '"
                                + board.initialState() + "'",
                        null));
            }
        }
        Set<String> reachableBackward = new HashSet<>();
        for (String terminal : terminals) {
            reachableBackward.addAll(reachable(terminal, incoming));
        }
        for (String state : states) {
            if (!terminals.contains(state) && !reachableBackward.contains(state)) {
                report.add(new BoardValidationError(id, "columns",
                        "DEAD_END_STATE",
                        "state '" + state + "' cannot reach any terminal column",
                        null));
            }
        }

        for (Map.Entry<String, ProcessorDefinition> entry : processorByState.entrySet()) {
            ProcessorDefinition proc = entry.getValue();
            if ("requeue".equalsIgnoreCase(proc.restartPolicy()) && !proc.idempotent()) {
                report.add(new BoardValidationError(id,
                        "columns[" + entry.getKey() + "].processor",
                        "REQUEUE_REQUIRES_IDEMPOTENT",
                        "restartPolicy=requeue requires idempotent=true (analysis §6.8)",
                        null));
            }
        }
    }

    private static Set<String> reachable(String start, Map<String, List<String>> edges) {
        Set<String> visited = new HashSet<>();
        if (start == null) return visited;
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String node = queue.pop();
            for (String neighbour : edges.getOrDefault(node, List.of())) {
                if (visited.add(neighbour)) queue.add(neighbour);
            }
        }
        return visited;
    }
}
