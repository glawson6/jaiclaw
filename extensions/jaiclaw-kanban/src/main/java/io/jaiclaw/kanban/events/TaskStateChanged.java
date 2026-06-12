package io.jaiclaw.kanban.events;

import io.jaiclaw.kanban.model.TransitionRecord;
import io.jaiclaw.tasks.TaskRecord;

/**
 * Spring {@code ApplicationEvent} payload carrying one accepted card
 * transition. The Phase 2 SSE stream and the Phase 3 column processor
 * manager both subscribe to this event — the shape is frozen at end of
 * Phase 1 per the plan's SPI freeze marker.
 *
 * <p>Phase 4 also promotes this to a first-class
 * {@code io.jaiclaw.core.hook.event.HookEvent} subtype; the
 * {@link KanbanHookFirer} bridges to the existing hook system in the
 * interim.
 *
 * @param transition  the accepted transition record
 * @param task        the persisted card immediately after the transition
 */
public record TaskStateChanged(TransitionRecord transition, TaskRecord task) {

    public String taskId()    { return transition.taskId(); }
    public String boardId()   { return transition.boardId(); }
    public String fromState() { return transition.fromState(); }
    public String toState()   { return transition.toState(); }
    public String event()     { return transition.event(); }
    public String actor()     { return transition.actor(); }
    public String tenantId()  { return transition.tenantId(); }
}
