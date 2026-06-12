package io.jaiclaw.kanban.state

import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.TerminalKind
import io.jaiclaw.kanban.model.TransitionDefinition
import io.jaiclaw.tasks.TaskDeliveryState
import io.jaiclaw.tasks.TaskRecord
import io.jaiclaw.tasks.TaskStatus
import spock.lang.Specification

import java.time.Instant

class TransitionGraphStateEngineSpec extends Specification {

    def engine = new TransitionGraphStateEngine()

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

    def "legal event is accepted with the right target state"() {
        when:
        def result = engine.fire(board(), card("backlog"), "START", [:])

        then:
        result.accepted()
        result.fromState() == "backlog"
        result.toState() == "drafting"
        result.reason() == null
    }

    def "unknown event is rejected with a helpful reason"() {
        when:
        def result = engine.fire(board(), card("drafting"), "BOGUS", [:])

        then:
        !result.accepted()
        result.reason().contains("no transition 'BOGUS'")
    }

    def "transition out of a state with no outgoing edge is rejected"() {
        when:
        def result = engine.fire(board(), card("done"), "APPROVE", [:])

        then:
        !result.accepted()
        result.reason().contains("from state 'done'")
    }

    def "WIP limit rejects transitions when the target column is full"() {
        when:
        def result = engine.fire(board(), card("backlog"), "START", [wipCount: 2])

        then:
        !result.accepted()
        result.reason().contains("WIP limit of 2")
    }

    def "WIP limit allows transitions when capacity is available"() {
        when:
        def result = engine.fire(board(), card("backlog"), "START", [wipCount: 1])

        then:
        result.accepted()
        result.toState() == "drafting"
    }

    def "allowedEvents returns the legal events from the current state"() {
        when:
        def events = engine.allowedEvents(board(), "drafting")

        then:
        events == ["SUBMIT"]
    }

    def "invalidate drops the cached graph"() {
        given: "the graph is compiled once"
        engine.fire(board(), card("backlog"), "START", [:])

        when:
        engine.invalidate("b1")

        then: "a subsequent call recompiles without error"
        engine.fire(board(), card("backlog"), "START", [:]).accepted()
    }
}
