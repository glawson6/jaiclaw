package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired by the kanban extension on every accepted card transition. The
 * payload mirrors the Spring {@code TaskStateChanged} application event
 * the kanban module publishes — promoting that shape to a first-class
 * {@link HookEvent} subtype gives plugins a typed channel that doesn't
 * piggyback on the {@code ToolCallStarted}/{@code ToolCallEnded} mapping
 * the Phase 1 {@code KanbanHookFirer} used.
 *
 * <p>{@link HookEvent#agentId()} carries the {@code boardId};
 * {@link HookEvent#sessionKey()} carries the {@code taskId}. Both
 * conventions match what the mapped firer wrote, so plugins that were
 * already pattern-matching on those fields keep working.
 *
 * <p>Migration: kanban's {@code KanbanHookFirer} now publishes this
 * event directly. The legacy mapped path (ToolCallStarted + ToolCallEnded)
 * is kept as a deprecated fallback for one release cycle so older plugins
 * keep receiving events while migrating their handlers.
 *
 * @param agentId    board id this transition belongs to
 * @param sessionKey task id of the moved card
 * @param timestamp  when the transition happened
 * @param boardId    same as {@link #agentId()}, surfaced for clarity
 * @param taskId     same as {@link #sessionKey()}, surfaced for clarity
 * @param fromState  state the card moved out of; {@code null} for CREATE
 * @param toState    state the card moved into
 * @param event      transition event name (e.g. START, APPROVE, RECOVERY)
 * @param actor      who fired the transition; {@code null} when system-driven
 * @param tenantId   tenant the card belongs to
 */
@Experimental
public record TaskStateChangedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String boardId,
        String taskId,
        String fromState,
        String toState,
        String event,
        String actor,
        String tenantId
) implements HookEvent {

    public static TaskStateChangedEvent of(String boardId, String taskId,
                                            String fromState, String toState,
                                            String event, String actor,
                                            String tenantId) {
        return new TaskStateChangedEvent(
                boardId, taskId, Instant.now(),
                boardId, taskId, fromState, toState, event, actor, tenantId);
    }
}
