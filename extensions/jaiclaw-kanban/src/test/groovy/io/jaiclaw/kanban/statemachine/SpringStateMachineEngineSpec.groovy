package io.jaiclaw.kanban.statemachine

import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.TerminalKind
import io.jaiclaw.kanban.model.TransitionDefinition
import io.jaiclaw.tasks.TaskDeliveryState
import io.jaiclaw.tasks.TaskRecord
import io.jaiclaw.tasks.TaskStatus
import spock.lang.Specification

import java.time.Instant

class SpringStateMachineEngineSpec extends Specification {

    BoardStateMachineFactory factory = new BoardStateMachineFactory()
    SpringStateMachineEngine engine = new SpringStateMachineEngine(factory)

    private BoardDefinition board() {
        new BoardDefinition("b1", "B1", [], "backlog", [
                new ColumnDefinition("backlog", "Backlog", TaskStatus.QUEUED, null, false, null, null),
                new ColumnDefinition("drafting", "Drafting", TaskStatus.RUNNING, 2, false, null, null),
                new ColumnDefinition("review", "Review", TaskStatus.RUNNING, null, false, null, null),
                new ColumnDefinition("done", "Done", TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [
                new TransitionDefinition("backlog", "drafting", "START", [:]),
                new TransitionDefinition("drafting", "review", "SUBMIT", [:]),
                new TransitionDefinition("review", "done", "APPROVE", [:]),
        ])
    }

    private TaskRecord card(String state) {
        new TaskRecord("t1", "T1", null, TaskStatus.QUEUED, TaskDeliveryState.PENDING,
                null, null, null, [:], Instant.now(), null, null, null,
                "b1", state, null, 0L, 0, null)
    }

    def "legal event is accepted and produces the expected target state"() {
        when:
        def result = engine.fire(board(), card("backlog"), "START", [:])

        then:
        result.accepted()
        result.toState() == "drafting"
    }

    def "unknown event is rejected with the same reason text the graph engine uses"() {
        when:
        def result = engine.fire(board(), card("drafting"), "BOGUS", [:])

        then:
        !result.accepted()
        result.reason().contains("no transition 'BOGUS'")
    }

    def "WIP-limit rejection matches the graph engine"() {
        when:
        def result = engine.fire(board(), card("backlog"), "START", [wipCount: 2])

        then:
        !result.accepted()
        result.reason().contains("WIP limit")
    }

    def "allowedEvents returns the events legal from the current state"() {
        when:
        def events = engine.allowedEvents(board(), "drafting")

        then:
        events == ["SUBMIT"]
    }

    def "factory caches machines and invalidate drops them"() {
        when:
        def m1 = factory.machineFor(board())
        def m2 = factory.machineFor(board())

        then:
        m1.is(m2)

        when:
        factory.invalidate("b1")
        def m3 = factory.machineFor(board())

        then:
        !m1.is(m3)
    }
}
