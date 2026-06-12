package io.jaiclaw.kanban.journal

import io.jaiclaw.kanban.events.TaskStateChanged
import io.jaiclaw.kanban.model.TransitionRecord
import io.jaiclaw.kanban.service.TransitionHistory
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit

class TransitionJournalSpec extends Specification {

    @TempDir
    Path tempDir

    Path journalDir
    TransitionHistory history
    TransitionJournal journal

    def setup() {
        journalDir = tempDir.resolve("journal")
        history = new TransitionHistory(50)
        journal = new TransitionJournal(journalDir, history, 50)
    }

    private TransitionRecord record(String taskId = "t1", String event = "START",
                                    Instant ts = Instant.now()) {
        new TransitionRecord(taskId, "b1", "backlog", "drafting",
                event, "alice", "default", ts)
    }

    def "append writes one JSONL line per transition under board-named file"() {
        when:
        journal.append(record("t1", "START"))
        journal.append(record("t2", "BLOCK"))

        then:
        Files.exists(journalDir.resolve("b1.jsonl"))
        Files.readAllLines(journalDir.resolve("b1.jsonl")).size() == 2
    }

    def "event listener appends on TaskStateChanged"() {
        when:
        journal.onTaskStateChanged(new TaskStateChanged(record("t1", "START"), null))

        then:
        Files.readAllLines(journalDir.resolve("b1.jsonl")).size() == 1
    }

    def "ignores events without a board id"() {
        when:
        journal.onTaskStateChanged(new TaskStateChanged(
                new TransitionRecord("t1", null, "a", "b", "GO", null, "default", Instant.now()),
                null))

        then:
        !Files.exists(journalDir.resolve("b1.jsonl"))
        notThrown(Throwable)
    }

    def "start() replays records into TransitionHistory in chronological order"() {
        given:
        // Seed three records out-of-order to confirm chronological replay.
        Instant base = Instant.parse("2026-06-12T10:00:00Z")
        journal.append(record("t1", "SUBMIT", base.plus(2, ChronoUnit.MINUTES)))
        journal.append(record("t1", "START",  base))
        journal.append(record("t1", "APPROVE", base.plus(5, ChronoUnit.MINUTES)))

        // Fresh history + journal instance — simulate restart.
        def fresh = new TransitionHistory(50)
        def newJournal = new TransitionJournal(journalDir, fresh, 50)

        when:
        newJournal.start()

        then:
        // forBoard returns newest-first; reverse for chronological assertion.
        def replayed = fresh.forBoard("b1", 50)*.event().reverse()
        replayed == ["START", "SUBMIT", "APPROVE"]
    }

    def "replayLimit caps how many entries land in the history deque"() {
        given:
        Instant base = Instant.parse("2026-06-12T10:00:00Z")
        15.times { i ->
            journal.append(record("t1", "E${i}", base.plus(i, ChronoUnit.SECONDS)))
        }
        def fresh = new TransitionHistory(50)
        def newJournal = new TransitionJournal(journalDir, fresh, 5)

        when:
        newJournal.start()

        then:
        // Only the last 5 chronological entries — E10..E14.
        def replayed = fresh.forBoard("b1", 50)*.event().reverse()
        replayed == ["E10", "E11", "E12", "E13", "E14"]
    }

    def "malformed jsonl line is skipped on replay"() {
        given:
        journal.append(record("t1", "START"))
        Files.writeString(journalDir.resolve("b1.jsonl"),
                Files.readString(journalDir.resolve("b1.jsonl")) + "{not json\n")
        journal.append(record("t1", "SUBMIT"))
        def fresh = new TransitionHistory(50)
        def newJournal = new TransitionJournal(journalDir, fresh, 50)

        when:
        newJournal.start()

        then:
        def replayed = fresh.forBoard("b1", 50)*.event() as Set
        replayed == ["START", "SUBMIT"] as Set
    }

    def "start is idempotent — second call is a no-op"() {
        given:
        journal.append(record("t1", "START"))
        def fresh = new TransitionHistory(50)
        def newJournal = new TransitionJournal(journalDir, fresh, 50)

        when:
        newJournal.start()
        newJournal.start()

        then:
        fresh.forBoard("b1", 50).size() == 1
    }

    def "empty journal dir replay returns zero boards"() {
        expect:
        journal.replayAll() == 0
    }

    def "missing journal dir replay returns zero boards"() {
        given:
        def nonexistent = new TransitionJournal(tempDir.resolve("nope"), history, 50)

        expect:
        nonexistent.replayAll() == 0
    }
}
