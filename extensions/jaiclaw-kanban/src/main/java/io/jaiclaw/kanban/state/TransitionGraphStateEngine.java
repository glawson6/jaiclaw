package io.jaiclaw.kanban.state;

import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.model.ColumnDefinition;
import io.jaiclaw.kanban.model.TransitionDefinition;
import io.jaiclaw.tasks.TaskRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lightweight in-process state engine. Precomputes a
 * {@code Map<state, Map<event, TransitionDefinition>>} per board, enforces
 * WIP limits via the per-target-column card count carried on
 * {@code context["wipCount"]}, and returns a {@link TransitionResult}.
 *
 * <p>Boards are cached after first compilation — subsequent transitions skip
 * the precompute step. The cache is keyed by board id; replacing a board
 * definition (Phase 2 dynamic registration) should clear it via
 * {@link #invalidate(String)}.
 */
public class TransitionGraphStateEngine implements TaskStateEngine {

    private final ConcurrentMap<String, Map<String, Map<String, TransitionDefinition>>> graphCache =
            new ConcurrentHashMap<>();

    @Override
    public TransitionResult fire(BoardDefinition board, TaskRecord task, String event,
                                 Map<String, Object> context) {
        String currentState = task.state() != null ? task.state() : board.initialState();
        Map<String, TransitionDefinition> transitions = graphFor(board)
                .getOrDefault(currentState, Map.of());
        TransitionDefinition transition = transitions.get(event);
        if (transition == null) {
            return TransitionResult.reject(currentState,
                    "no transition '" + event + "' from state '" + currentState + "'");
        }
        ColumnDefinition target = board.column(transition.to())
                .orElseThrow(() -> new StateEngineException(
                        "transition '" + event + "' targets unknown state '" + transition.to() + "'"));
        if (target.wipLimit() != null) {
            int incoming = wipCount(context);
            if (incoming >= target.wipLimit()) {
                return TransitionResult.reject(currentState,
                        "WIP limit of " + target.wipLimit()
                                + " reached on column '" + target.state() + "'");
            }
        }
        return TransitionResult.accept(currentState, transition.to());
    }

    @Override
    public List<String> allowedEvents(BoardDefinition board, String currentState) {
        String state = currentState != null ? currentState : board.initialState();
        return List.copyOf(graphFor(board).getOrDefault(state, Map.of()).keySet());
    }

    /** Drop the cached graph for {@code boardId} after a definition update. */
    public void invalidate(String boardId) {
        graphCache.remove(boardId);
    }

    private Map<String, Map<String, TransitionDefinition>> graphFor(BoardDefinition board) {
        return graphCache.computeIfAbsent(board.id(), id -> compile(board));
    }

    private Map<String, Map<String, TransitionDefinition>> compile(BoardDefinition board) {
        Map<String, Map<String, TransitionDefinition>> out = new HashMap<>();
        for (TransitionDefinition t : board.transitions()) {
            out.computeIfAbsent(t.from(), k -> new HashMap<>()).put(t.event(), t);
        }
        // Freeze inner maps to guard against accidental writers.
        Map<String, Map<String, TransitionDefinition>> frozen = new HashMap<>();
        out.forEach((state, perEvent) -> frozen.put(state, Map.copyOf(perEvent)));
        return Map.copyOf(frozen);
    }

    private static int wipCount(Map<String, Object> context) {
        if (context == null) return 0;
        Object v = context.get("wipCount");
        if (v instanceof Number n) return n.intValue();
        return 0;
    }
}
