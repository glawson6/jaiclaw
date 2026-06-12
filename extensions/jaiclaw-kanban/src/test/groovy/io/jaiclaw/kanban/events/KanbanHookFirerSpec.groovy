package io.jaiclaw.kanban.events

import io.jaiclaw.core.hook.event.HookEvent
import io.jaiclaw.core.hook.event.TaskStateChangedEvent
import io.jaiclaw.core.hook.event.ToolCallEndedEvent
import io.jaiclaw.core.hook.event.ToolCallStartedEvent
import io.jaiclaw.kanban.model.TransitionRecord
import io.jaiclaw.plugin.HookRunner
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Coverage for the Phase 4 first-class-event promotion in
 * {@link KanbanHookFirer} (plan §9 first group, §12 decision log).
 */
class KanbanHookFirerSpec extends Specification {

    CopyOnWriteArrayList<HookEvent> fired = new CopyOnWriteArrayList<>()
    // HookRunner is a concrete class, not an interface; subclass with a null
    // PluginRegistry and override fireVoid to capture the typed events for
    // assertion.
    HookRunner runner = new HookRunner(null) {
        @Override
        <E extends HookEvent> void fireVoid(E event) {
            fired.add(event)
        }
    }

    private TransitionRecord record() {
        new TransitionRecord("t1", "b1", "backlog", "drafting",
                "START", "alice", "tenant-a", Instant.now())
    }

    def "no-op when no HookRunner is wired"() {
        when:
        new KanbanHookFirer(null).fireTransition(record())

        then:
        notThrown(Throwable)
    }

    def "default firer emits TaskStateChangedEvent AND the mapped ToolCall pair"() {
        given:
        def firer = new KanbanHookFirer(runner)

        when:
        firer.fireTransition(record())

        then:
        fired.size() == 3
        fired[0] instanceof TaskStateChangedEvent
        fired[1] instanceof ToolCallStartedEvent
        fired[2] instanceof ToolCallEndedEvent

        and: "TaskStateChangedEvent carries the full payload"
        def tsc = fired[0] as TaskStateChangedEvent
        tsc.agentId() == "b1"
        tsc.sessionKey() == "t1"
        tsc.boardId() == "b1"
        tsc.taskId() == "t1"
        tsc.fromState() == "backlog"
        tsc.toState() == "drafting"
        tsc.event() == "START"
        tsc.actor() == "alice"
        tsc.tenantId() == "tenant-a"

        and: "legacy ToolCall pair uses agentId=board, sessionKey=task — same convention as Phase 1"
        (fired[1] as ToolCallStartedEvent).agentId() == "b1"
        (fired[1] as ToolCallStartedEvent).sessionKey() == "t1"
        (fired[1] as ToolCallStartedEvent).toolName() == "kanban.transition.START"
        (fired[2] as ToolCallEndedEvent).result() == "drafting"
    }

    def "legacyMapped=false fires ONLY the first-class event"() {
        given:
        def firer = new KanbanHookFirer(runner, false)

        when:
        firer.fireTransition(record())

        then:
        fired.size() == 1
        fired[0] instanceof TaskStateChangedEvent
    }

    def "CREATE transition (fromState=null) still produces a valid event"() {
        given:
        def firer = new KanbanHookFirer(runner, false)

        when:
        firer.fireTransition(new TransitionRecord(
                "t1", "b1", null, "backlog",
                "CREATE", null, "tenant-a", Instant.now()))

        then:
        fired.size() == 1
        (fired[0] as TaskStateChangedEvent).fromState() == null
        (fired[0] as TaskStateChangedEvent).toState() == "backlog"
    }
}
