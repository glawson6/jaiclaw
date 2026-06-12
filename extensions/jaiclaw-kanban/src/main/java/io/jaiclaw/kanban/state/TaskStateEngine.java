package io.jaiclaw.kanban.state;

import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.tasks.TaskRecord;

import java.util.List;
import java.util.Map;

/**
 * SPI for the engine that decides whether and how a card moves between
 * columns. Phase 1 ships {@link TransitionGraphStateEngine} as the default
 * lightweight implementation; Phase 3 adds an optional Spring State Machine
 * implementation behind {@code @ConditionalOnClass}.
 *
 * <p>Engines are pure logic — they do not persist. The
 * {@code TaskTransitionService} is responsible for taking an
 * {@link TransitionResult#accepted accepted} result and persisting the
 * updated card via {@code TaskStore.compareAndSave}.
 */
public interface TaskStateEngine {

    /**
     * Compute the result of firing {@code event} on {@code task} under
     * {@code board}.
     *
     * @param board   the board the card belongs to
     * @param task    the card in its current state
     * @param event   the transition event name
     * @param context optional extra context (e.g. column WIP counts) — may be empty
     * @return accepted result with target state, or a rejected result with reason
     */
    TransitionResult fire(BoardDefinition board, TaskRecord task, String event,
                          Map<String, Object> context);

    /** Events currently legal from {@code currentState} on the given board. */
    List<String> allowedEvents(BoardDefinition board, String currentState);
}
