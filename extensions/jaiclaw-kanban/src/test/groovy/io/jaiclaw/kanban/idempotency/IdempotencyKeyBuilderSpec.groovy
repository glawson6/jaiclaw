package io.jaiclaw.kanban.idempotency

import io.jaiclaw.kanban.model.TransitionRecord
import io.jaiclaw.kanban.service.TransitionHistory
import io.jaiclaw.tasks.TaskDeliveryState
import io.jaiclaw.tasks.TaskRecord
import io.jaiclaw.tasks.TaskStatus
import spock.lang.Specification

import java.time.Instant

class IdempotencyKeyBuilderSpec extends Specification {

    TransitionHistory history = new TransitionHistory(50)
    IdempotencyKeyBuilder builder = new IdempotencyKeyBuilder(history)

    private TaskRecord card(String taskId, String state) {
        new TaskRecord(taskId, taskId, null, TaskStatus.RUNNING, TaskDeliveryState.PENDING,
                null, null, null, [:], Instant.now(), null, null, "default",
                "b1", state, null, 0L, 0, null)
    }

    def "key includes board, task, state and entrySeq=1 on first entry"() {
        given:
        history.record(new TransitionRecord("t1", "b1", "backlog", "drafting",
                "START", null, "default", Instant.now()))

        when:
        def key = builder.build(card("t1", "drafting"))

        then:
        key == "b1:t1:drafting:1"
    }

    def "entrySeq increments on legitimate re-entry"() {
        given:
        history.record(new TransitionRecord("t1", "b1", "backlog", "drafting",
                "START", null, "default", Instant.now()))
        history.record(new TransitionRecord("t1", "b1", "drafting", "review",
                "SUBMIT", null, "default", Instant.now()))
        history.record(new TransitionRecord("t1", "b1", "review", "drafting",
                "REJECT", null, "default", Instant.now()))

        when:
        def key = builder.build(card("t1", "drafting"))

        then:
        key == "b1:t1:drafting:2"
    }

    def "key is stable across calls when nothing in history changed"() {
        given:
        history.record(new TransitionRecord("t1", "b1", "backlog", "drafting",
                "START", null, "default", Instant.now()))

        expect:
        builder.build(card("t1", "drafting")) == builder.build(card("t1", "drafting"))
    }

    def "different cards yield different keys"() {
        given:
        history.record(new TransitionRecord("t1", "b1", "backlog", "drafting",
                "START", null, "default", Instant.now()))
        history.record(new TransitionRecord("t2", "b1", "backlog", "drafting",
                "START", null, "default", Instant.now()))

        expect:
        builder.build(card("t1", "drafting")) != builder.build(card("t2", "drafting"))
    }

    def "build with missing boardId or state throws"() {
        when:
        builder.build(new TaskRecord("t1", "T1", null, TaskStatus.QUEUED,
                TaskDeliveryState.PENDING, null, null, null, [:], Instant.now(),
                null, null, null,
                null, "drafting", null, 0L, 0, null))

        then:
        thrown(IllegalArgumentException)
    }
}
