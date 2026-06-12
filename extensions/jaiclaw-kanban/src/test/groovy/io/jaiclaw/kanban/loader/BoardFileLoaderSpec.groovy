package io.jaiclaw.kanban.loader

import org.springframework.core.io.DefaultResourceLoader
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class BoardFileLoaderSpec extends Specification {

    @TempDir
    Path tempDir

    def loader = new BoardFileLoader(new DefaultResourceLoader())

    def "loads a board from a file: pattern"() {
        given:
        def yaml = tempDir.resolve("my-board.yaml")
        Files.writeString(yaml, """
            id: my-board
            initialState: a
            columns:
              - { state: a, phase: QUEUED }
              - { state: b, phase: SUCCEEDED, terminal: true, terminalKind: SUCCESS }
            transitions:
              - { from: a, to: b, event: GO }
        """.stripIndent())

        when:
        def boards = loader.loadAll(["file:${tempDir}/*.yaml" as String])

        then:
        boards.size() == 1
        boards[0].id() == "my-board"
        boards[0].columns()*.state() == ["a", "b"]
    }

    def "loads a board from classpath"() {
        when:
        def boards = loader.loadAll(["classpath:boards/e2e-content-review.yaml"])

        then:
        boards.size() == 1
        boards[0].id() == "e2e-content-review"
    }

    def "filename stem is the fallback id when YAML omits one"() {
        given:
        def yaml = tempDir.resolve("nameless.yaml")
        Files.writeString(yaml, """
            initialState: a
            columns:
              - { state: a, phase: QUEUED }
              - { state: b, phase: SUCCEEDED, terminal: true, terminalKind: SUCCESS }
            transitions: []
        """.stripIndent())

        when:
        def boards = loader.loadAll(["file:${tempDir}/*.yaml" as String])

        then:
        boards.size() == 1
        boards[0].id() == "nameless"
    }

    def "skips a corrupt file and continues with the rest"() {
        given:
        Files.writeString(tempDir.resolve("good.yaml"), """
            id: good
            initialState: a
            columns:
              - { state: a, phase: QUEUED }
              - { state: b, phase: SUCCEEDED, terminal: true, terminalKind: SUCCESS }
            transitions: []
        """.stripIndent())
        Files.writeString(tempDir.resolve("bad.yaml"), "{not yaml")

        when:
        def boards = loader.loadAll(["file:${tempDir}/*.yaml" as String])

        then:
        boards*.id() == ["good"]
    }

    def "de-dups across overlapping patterns"() {
        given:
        def yaml = tempDir.resolve("only.yaml")
        Files.writeString(yaml, """
            id: only
            initialState: a
            columns:
              - { state: a, phase: QUEUED }
              - { state: b, phase: SUCCEEDED, terminal: true, terminalKind: SUCCESS }
            transitions: []
        """.stripIndent())

        when:
        def boards = loader.loadAll([
                "file:${tempDir}/*.yaml" as String,
                "file:${tempDir}/only.yaml" as String,
        ])

        then:
        boards.size() == 1
    }
}
