package io.jaiclaw.kanban.events;

import io.jaiclaw.core.hook.event.TaskStateChangedEvent;
import io.jaiclaw.core.hook.event.ToolCallEndedEvent;
import io.jaiclaw.core.hook.event.ToolCallStartedEvent;
import io.jaiclaw.kanban.model.TransitionRecord;
import io.jaiclaw.plugin.HookRunner;

/**
 * Publishes kanban transitions through the existing {@link HookRunner}
 * event system.
 *
 * <p>Two channels (analysis §5.2 / plan §6.4 + Phase 4 promotion):
 * <ul>
 *   <li>{@link TaskStateChangedEvent} — first-class {@code HookEvent}
 *       subtype added in Phase 4. The primary channel for kanban-aware
 *       plugins.</li>
 *   <li>Paired {@link ToolCallStartedEvent} / {@link ToolCallEndedEvent}
 *       with {@code agentId = boardId} and {@code sessionKey = taskId} —
 *       the Phase 1 mapped path, kept as a deprecated fallback for one
 *       release cycle so observability plugins (audit recorder, trajectory
 *       recorder, pipeline tracker) that were written against
 *       {@code ToolCallStartedEvent} keep capturing kanban activity while
 *       they migrate.</li>
 * </ul>
 *
 * <p>The mapped channel can be disabled via
 * {@code jaiclaw.kanban.hooks.legacy-mapped} (default {@code true} in
 * the 0.8 series; will flip to {@code false} in 0.9, then be removed).
 *
 * <p>No-ops gracefully when {@code plugin-sdk} is absent
 * ({@code hookRunner == null}).
 */
public class KanbanHookFirer {

    private final HookRunner hookRunner;
    private final boolean emitLegacyMapped;

    public KanbanHookFirer(HookRunner hookRunner) {
        this(hookRunner, true);
    }

    public KanbanHookFirer(HookRunner hookRunner, boolean emitLegacyMapped) {
        this.hookRunner = hookRunner;
        this.emitLegacyMapped = emitLegacyMapped;
    }

    public void fireTransition(TransitionRecord record) {
        if (hookRunner == null) return;
        // Primary channel: typed kanban event.
        hookRunner.fireVoid(TaskStateChangedEvent.of(
                record.boardId(),
                record.taskId(),
                record.fromState(),
                record.toState(),
                record.event(),
                record.actor(),
                record.tenantId()));

        if (!emitLegacyMapped) return;

        // Deprecated fallback — paired tool-call events for pre-Phase-4 plugins.
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
