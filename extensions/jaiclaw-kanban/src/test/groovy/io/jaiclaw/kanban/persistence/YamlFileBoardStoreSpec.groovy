package io.jaiclaw.kanban.persistence

import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.TerminalKind
import io.jaiclaw.kanban.model.TransitionDefinition
import io.jaiclaw.tasks.TaskStatus
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class YamlFileBoardStoreSpec extends Specification {

    @TempDir
    Path tempDir

    private BoardDefinition board(String id) {
        new BoardDefinition(id, id.toUpperCase(), [], "backlog", [
                new ColumnDefinition("backlog", "Backlog", TaskStatus.QUEUED, null, false, null, null),
                new ColumnDefinition("done", "Done", TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [
                new TransitionDefinition("backlog", "done", "FINISH", [:]),
        ])
    }

    def "save writes one YAML file per board"() {
        given:
        def store = new YamlFileBoardStore(tempDir)

        when:
        store.save(board("alpha"))
        store.save(board("beta"))

        then:
        Files.exists(tempDir.resolve("alpha.yaml"))
        Files.exists(tempDir.resolve("beta.yaml"))
        !Files.exists(tempDir.resolve("alpha.yaml.tmp"))
        store.count() == 2L
    }

    def "save -> reload roundtrip preserves the definition"() {
        given:
        def store = new YamlFileBoardStore(tempDir)

        when:
        store.save(board("rt"))
        def reread = new YamlFileBoardStore(tempDir).findById("rt")

        then:
        reread.isPresent()
        reread.get().id() == "rt"
        reread.get().columns()*.state() == ["backlog", "done"]
        reread.get().transitions()[0].event() == "FINISH"
    }

    def "delete removes the YAML file"() {
        given:
        def store = new YamlFileBoardStore(tempDir)
        store.save(board("gone"))

        when:
        def removed = store.delete("gone")

        then:
        removed
        !Files.exists(tempDir.resolve("gone.yaml"))
        store.findById("gone").isEmpty()
    }

    def "findAll returns boards in stable id order"() {
        given:
        def store = new YamlFileBoardStore(tempDir)
        store.save(board("c"))
        store.save(board("a"))
        store.save(board("b"))

        when:
        def all = store.findAll()

        then:
        all*.id() == ["a", "b", "c"]
    }

    def "save is atomic — no tmp file lingers after success"() {
        given:
        def store = new YamlFileBoardStore(tempDir)

        when:
        store.save(board("atomic"))

        then:
        Files.list(tempDir).noneMatch { it.fileName.toString().endsWith(".tmp") }
    }

    def "malformed YAML file is skipped on bulk read"() {
        given:
        def store = new YamlFileBoardStore(tempDir)
        store.save(board("good"))
        Files.writeString(tempDir.resolve("broken.yaml"), "{not yaml")

        when:
        def all = store.findAll()

        then:
        all.size() == 1
        all[0].id() == "good"
    }
}
