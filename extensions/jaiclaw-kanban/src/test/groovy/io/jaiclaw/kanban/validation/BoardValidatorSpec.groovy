package io.jaiclaw.kanban.validation

import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.ProcessorDefinition
import io.jaiclaw.kanban.model.TerminalKind
import io.jaiclaw.kanban.model.TransitionDefinition
import io.jaiclaw.tasks.TaskStatus
import spock.lang.Specification

class BoardValidatorSpec extends Specification {

    def validator = new BoardValidator()

    private BoardDefinition good() {
        new BoardDefinition("ok", "OK", [], "a", [
                new ColumnDefinition("a", "A", TaskStatus.QUEUED, null, false, null, null),
                new ColumnDefinition("b", "B", TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [
                new TransitionDefinition("a", "b", "GO", [:]),
        ])
    }

    def "a well-formed board produces no errors"() {
        expect:
        !validator.validate(good()).hasErrors()
    }

    def "unknown initialState is reported with a suggestion"() {
        given:
        def board = new BoardDefinition("bad", "B", [], "ay", good().columns(), good().transitions())

        when:
        def report = validator.validate(board)

        then:
        report.hasErrors()
        report.errors().any { it.code() == "UNKNOWN_INITIAL_STATE" && it.suggestion() == "a" }
    }

    def "unknown transition.to is reported"() {
        given:
        def board = new BoardDefinition("bad", "B", [], "a", good().columns(), [
                new TransitionDefinition("a", "bogus", "GO", [:]),
        ])

        when:
        def report = validator.validate(board)

        then:
        report.hasErrors()
        report.errors().any { it.code() == "UNKNOWN_TO_STATE" }
    }

    def "a board with no terminal column is rejected"() {
        given:
        def board = new BoardDefinition("bad", "B", [], "a", [
                new ColumnDefinition("a", "A", TaskStatus.QUEUED, null, false, null, null),
                new ColumnDefinition("b", "B", TaskStatus.RUNNING, null, false, null, null),
        ], [
                new TransitionDefinition("a", "b", "GO", [:]),
        ])

        when:
        def report = validator.validate(board)

        then:
        report.hasErrors()
        report.errors().any { it.code() == "NO_TERMINAL_COLUMN" }
    }

    def "an unreachable column is reported"() {
        given:
        def board = new BoardDefinition("bad", "B", [], "a", [
                new ColumnDefinition("a", "A", TaskStatus.QUEUED, null, false, null, null),
                new ColumnDefinition("b", "B", TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
                new ColumnDefinition("orphan", "Orphan", TaskStatus.QUEUED, null, false, null, null),
        ], [
                new TransitionDefinition("a", "b", "GO", [:]),
        ])

        when:
        def report = validator.validate(board)

        then:
        report.hasErrors()
        report.errors().any { it.code() == "UNREACHABLE_STATE" && it.message().contains("orphan") }
    }

    def "requeue requires idempotent=true on the same processor"() {
        given:
        def processor = new ProcessorDefinition(
                "agent", "draft {{name}}", "review", "blocked",
                false, "requeue", 3, [:])
        def board = new BoardDefinition("bad", "B", [], "a", [
                new ColumnDefinition("a", "A", TaskStatus.QUEUED, null, false, null, processor),
                new ColumnDefinition("b", "B", TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [
                new TransitionDefinition("a", "b", "GO", [:]),
        ])

        when:
        def report = validator.validate(board)

        then:
        report.hasErrors()
        report.errors().any { it.code() == "REQUEUE_REQUIRES_IDEMPOTENT" }
    }

    def "validateOrThrow throws on errors"() {
        when:
        validator.validateOrThrow([new BoardDefinition("bad", "B", [], "missing",
                good().columns(), good().transitions())])

        then:
        thrown(IllegalStateException)
    }
}
