package io.jaiclaw.kanban.statemachine;

import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.model.ColumnDefinition;
import io.jaiclaw.kanban.model.TransitionDefinition;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Builds a {@link StateMachine} per {@link BoardDefinition} at runtime
 * from the board's columns and transitions — no annotations, no
 * compile-time states. Machines are cached per board id; replacing a
 * board definition invalidates the cache via {@link #invalidate}.
 *
 * <p>Used by {@link SpringStateMachineEngine}. The factory itself is
 * pure logic — the engine handles statefulness (rehydrate, fire,
 * release) per transition.
 */
public class BoardStateMachineFactory {

    private final ConcurrentMap<String, StateMachine<String, String>> cache = new ConcurrentHashMap<>();

    /** Cached machine for {@code board}, building one on first request. */
    public StateMachine<String, String> machineFor(BoardDefinition board) {
        return cache.computeIfAbsent(board.id(), id -> build(board));
    }

    public void invalidate(String boardId) {
        cache.remove(boardId);
    }

    private StateMachine<String, String> build(BoardDefinition board) {
        try {
            StateMachineBuilder.Builder<String, String> builder = StateMachineBuilder.builder();
            Set<String> ends = new HashSet<>();
            for (ColumnDefinition col : board.columns()) {
                if (col.terminal()) ends.add(col.state());
            }
            Set<String> states = new HashSet<>();
            for (ColumnDefinition col : board.columns()) {
                states.add(col.state());
            }
            builder.configureStates()
                    .withStates()
                    .initial(board.initialState())
                    .states(states)
                    .end(ends.isEmpty() ? null : ends.iterator().next());
            for (TransitionDefinition transition : board.transitions()) {
                builder.configureTransitions()
                        .withExternal()
                        .source(transition.from())
                        .target(transition.to())
                        .event(transition.event())
                        .and();
            }
            return builder.build();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to build StateMachine for board '" + board.id() + "': " + ex.getMessage(),
                    ex);
        }
    }
}
