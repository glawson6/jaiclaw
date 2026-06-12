package io.jaiclaw.kanban.web.readonly

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.TerminalKind
import io.jaiclaw.kanban.model.TransitionDefinition
import io.jaiclaw.tasks.JsonFileTaskStore
import io.jaiclaw.tasks.TaskStatus
import io.jaiclaw.tasks.TaskStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

/**
 * Read-only mode: {@code jaiclaw.kanban.boards.writable=false} makes
 * {@code POST /boards} and {@code DELETE /boards/{id}} return 405. Card
 * mutations remain available (analysis §9 Q1 resolution).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = [KanbanBoardControllerReadOnlySpec.TestApp])
@TestPropertySource(properties = [
        "jaiclaw.kanban.enabled=true",
        "jaiclaw.kanban.boards.writable=false",
])
class KanbanBoardControllerReadOnlySpec extends Specification {

    static final Path tempDir = Files.createTempDirectory("kanban-ro-spec")

    @DynamicPropertySource
    static void registerStorageDirs(DynamicPropertyRegistry registry) {
        Path boardsDir = tempDir.resolve("boards")
        Files.createDirectories(boardsDir)
        // Seed the read-only store with one valid board so card endpoints have something to drive.
        Files.writeString(boardsDir.resolve("ro.yaml"), """
            id: ro
            name: Read Only Board
            initialState: backlog
            columns:
              - { state: backlog,  phase: QUEUED }
              - { state: done,     phase: SUCCEEDED, terminal: true, terminalKind: SUCCESS }
            transitions:
              - { from: backlog, to: done, event: FINISH }
        """.stripIndent())
        registry.add("jaiclaw.kanban.boards-dir", { boardsDir.toString() })
    }

    @LocalServerPort
    int port

    @Autowired
    TestRestTemplate http

    private String url(String path) { "http://localhost:${port}${path}" }

    def "POST /boards returns 405 when writable=false"() {
        when:
        def response = http.postForEntity(url("/api/kanban/boards"),
                new BoardDefinition("new", "New", [], "backlog", [
                        new ColumnDefinition("backlog", "B", TaskStatus.QUEUED, null, false, null, null),
                        new ColumnDefinition("done",    "D", TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
                ], [new TransitionDefinition("backlog", "done", "GO", [:])]),
                Map)

        then:
        response.statusCode == HttpStatus.METHOD_NOT_ALLOWED
        response.body.error.toString().contains("read-only")
    }

    def "DELETE /boards/{id} returns 405 when writable=false"() {
        when:
        def response = http.exchange(url("/api/kanban/boards/ro"),
                HttpMethod.DELETE, new HttpEntity<Void>(), Map)

        then:
        response.statusCode == HttpStatus.METHOD_NOT_ALLOWED
    }

    def "POST /boards/{id}/tasks still creates cards in read-only board mode"() {
        when:
        def response = http.postForEntity(url("/api/kanban/boards/ro/tasks"),
                [name: "Should work"], Map)

        then:
        response.statusCode == HttpStatus.CREATED
        response.body.boardId == "ro"
        response.body.state == "backlog"
    }

    @SpringBootApplication
    static class TestApp {
        @Bean
        TaskStore readOnlyControllerSpecTaskStore() {
            Path dir = Files.createTempDirectory("kanban-readonly-test-tasks")
            new JsonFileTaskStore(dir, new TenantGuard(TenantProperties.DEFAULT))
        }
    }
}
