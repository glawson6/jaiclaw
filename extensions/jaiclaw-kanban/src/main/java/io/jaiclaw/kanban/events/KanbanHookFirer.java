package io.jaiclaw.kanban.events;

import io.jaiclaw.core.hook.event.ToolCallEndedEvent;
import io.jaiclaw.core.hook.event.ToolCallStartedEvent;
import io.jaiclaw.kanban.model.TransitionRecord;
import io.jaiclaw.plugin.HookRunner;

/**
 * Bridges kanban transitions onto the existing {@link HookRunner} event
 * system as paired {@link ToolCallStartedEvent} + {@link ToolCallEndedEvent}.
 *
 * <p>This is the mapped-event Phase-1 path (analysis §5.2 / plan §6.4). It
 * lets existing observability plugins (the audit/trajectory recorders, the
 * pipeline tracker) capture kanban activity without any change to
 * {@code jaiclaw-core}'s sealed {@code HookEvent} hierarchy. Phase 4 promotes
 * the kanban event to a first-class {@code HookEvent} subtype; this firer
 * keeps working as a deprecated fallback for one release cycle after that.
 *
 * <p>{@code agentId} carries the board id and {@code sessionKey} carries the
 * task id, so plugins can distinguish kanban activity from real agent calls.
 *
 * <p>No-ops gracefully when {@code plugin-sdk} is absent
 * ({@code hookRunner == null}).
 */
public class KanbanHookFirer {

    private final HookRunner hookRunner;

    public KanbanHookFirer(HookRunner hookRunner) {
        this.hookRunner = hookRunner;
    }

    public void fireTransition(TransitionRecord record) {
        if (hookRunner == null) return;
        String parameters = "from=" + record.fromState()
                + " to=" + record.toState()
                + (record.actor() != null ? " actor=" + record.actor() : "");
        hookRunner.fireVoid(ToolCallStartedEvent.of(
                record.boardId(),
                record.taskId(),
                "kanban.transition." + record.event(),
                parameters,
                0));
        hookRunner.fireVoid(ToolCallEndedEvent.of(
                record.boardId(),
                record.taskId(),
                "kanban.transition." + record.event(),
                parameters,
                record.toState(),
                0));
    }
}
