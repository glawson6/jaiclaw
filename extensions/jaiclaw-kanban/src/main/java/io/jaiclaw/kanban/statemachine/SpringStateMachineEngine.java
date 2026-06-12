package io.jaiclaw.kanban.statemachine;

import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.model.ColumnDefinition;
import io.jaiclaw.kanban.model.TransitionDefinition;
import io.jaiclaw.kanban.state.StateEngineException;
import io.jaiclaw.kanban.state.TaskStateEngine;
import io.jaiclaw.kanban.state.TransitionResult;
import io.jaiclaw.tasks.TaskRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link TaskStateEngine} backed by Spring State Machine. Builds the
 * machine at runtime from {@link BoardDefinition}; **does not** keep a
 * live machine per task. Each call walks the precomputed transition list
 * directly — the SSM machine is built only to validate the graph
 * structure (and to surface configuration errors early).
 *
 * <p>Why not actually run events through the SSM machine? In practice,
 * an SSM transition under SSM 4.x is reactive and async — for a flat
 * kanban graph driving a state field on a record, the overhead is
 * pure cost. Building the machine through the {@link BoardStateMachineFactory}
 * still gives us SSM's validation (cycles, unreachable states, etc.); the
 * actual fire logic is the same as the graph engine. This satisfies the
 * Phase 3 plan's "stateless usage — don't keep a live StateMachine per
 * task" rule by being even more stateless.
 *
 * <p>WIP-limit enforcement matches {@link io.jaiclaw.kanban.state.TransitionGraphStateEngine}
 * exactly (analysis §3.3 — same semantics under either engine).
 *
 * <p>Activated by {@code jaiclaw.kanban.engine: spring-statemachine} +
 * {@code spring-statemachine-core} on the classpath.
 */
public class SpringStateMachineEngine implements TaskStateEngine {

    private static final Logger log = LoggerFactory.getLogger(SpringStateMachineEngine.class);

    private final BoardStateMachineFactory factory;

    public SpringStateMachineEngine(BoardStateMachineFactory factory) {
        this.factory = factory;
    }

    @Override
    public TransitionResult fire(BoardDefinition board, TaskRecord task, String event,
                                 Map<String, Object> context) {
        // Force-build the machine to surface graph errors early. The result
        // is cached, so repeated transitions don't rebuild.
        try {
            Objects.requireNonNull(factory.machineFor(board));
        } catch (RuntimeException ex) {
            throw new StateEngineException(
                    "SSM build failed for board '" + board.id() + "'", ex);
        }

        String currentState = task.state() != null ? task.state() : board.initialState();
        TransitionDefinition match = board.transitions().stream()
                .filter(t -> t.from().equals(currentState) && t.event().equals(event))
                .findFirst()
                .orElse(null);
        if (match == null) {
            return TransitionResult.reject(currentState,
                    "no transition '" + event + "' from state '" + currentState + "'");
        }
        ColumnDefinition target = board.column(match.to())
                .orElseThrow(() -> new StateEngineException(
                        "transition '" + event + "' targets unknown state '" + match.to() + "'"));
        if (target.wipLimit() != null) {
            int incoming = wipCount(context);
            if (incoming >= target.wipLimit()) {
                return TransitionResult.reject(currentState,
                        "WIP limit of " + target.wipLimit()
                                + " reached on column '" + target.state() + "'");
            }
        }
        return TransitionResult.accept(currentState, match.to());
    }

    @Override
    public List<String> allowedEvents(BoardDefinition board, String currentState) {
        String state = currentState != null ? currentState : board.initialState();
        return board.transitions().stream()
                .filter(t -> t.from().equals(state))
                .map(TransitionDefinition::event)
                .distinct()
                .toList();
    }

    /** Drop the cached SSM machine for {@code boardId}. */
    public void invalidate(String boardId) {
        factory.invalidate(boardId);
    }

    private static int wipCount(Map<String, Object> context) {
        if (context == null) return 0;
        Object v = context.get("wipCount");
        if (v instanceof Number n) return n.intValue();
        return 0;
    }
}
