package io.jaiclaw.kanban.web.writable

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
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

/**
 * Full-stack integration spec for {@link KanbanBoardController}. Boots
 * the kanban auto-configs alongside Spring Web and exercises every REST
 * endpoint end-to-end against a real on-disk YAML board store and JSON
 * task store.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = [KanbanBoardControllerSpec.TestApp])
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = [
        "jaiclaw.kanban.enabled=true",
])
class KanbanBoardControllerSpec extends Specification {

    static final Path tempDir = Files.createTempDirectory("kanban-ctl-spec")

    @DynamicPropertySource
    static void registerStorageDirs(DynamicPropertyRegistry registry) {
        registry.add("jaiclaw.kanban.boards-dir", { tempDir.resolve("boards").toString() })
    }

    @LocalServerPort
    int port

    @Autowired
    TestRestTemplate http

    @Autowired
    io.jaiclaw.kanban.service.KanbanBoardService boardService

    @Autowired
    io.jaiclaw.kanban.persistence.BoardStore boardStore

    @Autowired
    io.jaiclaw.tasks.TaskStore taskStore

    @Autowired
    io.jaiclaw.kanban.service.TransitionHistory transitionHistory

    def setup() {
        // Wipe per-test state so specs run independently regardless of order.
        boardService.listAllUnscoped().each { boardService.remove(it.id()) }
        taskStore.findAll().each { taskStore.deleteById(it.id()) }
        // Drain history for the fixture board id.
        transitionHistory.clear("ctl")
    }

    private String url(String path) { "http://localhost:${port}${path}" }

    private void registerFixtureBoard() {
        BoardDefinition board = fixtureBoard()
        def response = http.postForEntity(url("/api/kanban/boards"), board, Map)
        assert response.statusCode == HttpStatus.CREATED
    }

    private BoardDefinition fixtureBoard() {
        new BoardDefinition("ctl", "Controller Board", [], "backlog", [
                new ColumnDefinition("backlog",  "Backlog",  TaskStatus.QUEUED,  null, false, null, null),
                new ColumnDefinition("drafting", "Drafting", TaskStatus.RUNNING, 1,    false, null, null),
                new ColumnDefinition("done",     "Done",     TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [
                new TransitionDefinition("backlog",  "drafting", "START", [:]),
                new TransitionDefinition("drafting", "done",     "FINISH", [:]),
        ])
    }

    def "POST /boards registers a board, persists YAML, GET round-trips"() {
        when:
        def post = http.postForEntity(url("/api/kanban/boards"), fixtureBoard(), Map)

        then:
        post.statusCode == HttpStatus.CREATED
        post.body.id == "ctl"

        when:
        def list = http.getForEntity(url("/api/kanban/boards"), List)

        then:
        list.statusCode == HttpStatus.OK
        list.body*.id.contains("ctl")

        and: "the YAML file is on disk"
        java.nio.file.Files.exists(tempDir.resolve("boards/ctl.yaml"))
    }

    def "GET /boards/{id}/snapshot returns columns + cards"() {
        given:
        registerFixtureBoard()

        when: "two cards land in backlog"
        def c1 = http.postForEntity(url("/api/kanban/boards/ctl/tasks"),
                [name: "First card",  description: "desc 1"], Map).body
        def c2 = http.postForEntity(url("/api/kanban/boards/ctl/tasks"),
                [name: "Second card", description: "desc 2"], Map).body

        and:
        def snap = http.getForEntity(url("/api/kanban/boards/ctl/snapshot"), Map)

        then:
        snap.statusCode == HttpStatus.OK
        snap.body.boardId == "ctl"
        def columns = snap.body.columns as List
        columns.find { it.state == "backlog" }.cards*.id as Set == [c1.id, c2.id] as Set
        snap.body.totalCards == 2
    }

    def "POST /tasks/{id}/transition advances the card on the happy path"() {
        given:
        registerFixtureBoard()
        def card = http.postForEntity(url("/api/kanban/boards/ctl/tasks"),
                [name: "Walker"], Map).body

        when:
        def t1 = http.postForEntity(url("/api/kanban/tasks/${card.id}/transition"),
                [event: "START", actor: "alice"], Map)
        def t2 = http.postForEntity(url("/api/kanban/tasks/${card.id}/transition"),
                [event: "FINISH", actor: "bob"], Map)

        then:
        t1.statusCode == HttpStatus.OK
        t1.body.toState == "drafting"
        t2.statusCode == HttpStatus.OK
        t2.body.toState == "done"

        and:
        def reread = http.getForEntity(url("/api/kanban/tasks/${card.id}"), Map)
        reread.body.state == "done"
        reread.body.allowedEvents == []
    }

    def "POST /tasks/{id}/transition returns 409 on a bogus event"() {
        given:
        registerFixtureBoard()
        def card = http.postForEntity(url("/api/kanban/boards/ctl/tasks"),
                [name: "Bogus"], Map).body

        when:
        def response = http.postForEntity(url("/api/kanban/tasks/${card.id}/transition"),
                [event: "DOES_NOT_EXIST"], Map)

        then:
        response.statusCode == HttpStatus.CONFLICT
        response.body.error == "transition rejected"
        response.body.reason.toString().contains("no transition")
    }

    def "POST /tasks/{id}/transition returns 409 when WIP limit is exceeded"() {
        given:
        registerFixtureBoard()
        def first = http.postForEntity(url("/api/kanban/boards/ctl/tasks"),
                [name: "First"],  Map).body
        def second = http.postForEntity(url("/api/kanban/boards/ctl/tasks"),
                [name: "Second"], Map).body

        and: "first card moves into drafting (which has wipLimit=1)"
        def firstStart = http.postForEntity(url("/api/kanban/tasks/${first.id}/transition"),
                [event: "START"], Map)
        firstStart.statusCode == HttpStatus.OK

        when: "second card tries to enter the full column"
        def response = http.postForEntity(url("/api/kanban/tasks/${second.id}/transition"),
                [event: "START"], Map)

        then:
        response.statusCode == HttpStatus.CONFLICT
        response.body.reason.toString().contains("WIP limit")
    }

    def "POST /tasks/{id}/claim sets assignee and returns the updated card"() {
        given:
        registerFixtureBoard()
        def card = http.postForEntity(url("/api/kanban/boards/ctl/tasks"),
                [name: "Claimable"], Map).body

        when:
        def response = http.postForEntity(url("/api/kanban/tasks/${card.id}/claim"),
                [assignee: "alice"], Map)

        then:
        response.statusCode == HttpStatus.OK
        response.body.assignee == "alice"
    }

    def "GET /boards/{id}/history returns recorded transitions newest-first"() {
        given:
        registerFixtureBoard()
        def card = http.postForEntity(url("/api/kanban/boards/ctl/tasks"),
                [name: "Historian"], Map).body
        http.postForEntity(url("/api/kanban/tasks/${card.id}/transition"),
                [event: "START"], Map)

        when:
        def response = http.getForEntity(url("/api/kanban/boards/ctl/history?limit=5"), List)

        then:
        response.statusCode == HttpStatus.OK
        response.body.size() >= 2
        response.body*.event.contains("CREATE")
        response.body*.event.contains("START")
    }

    def "GET /boards/{id}/ascii returns text/plain rendering"() {
        given:
        registerFixtureBoard()
        http.postForEntity(url("/api/kanban/boards/ctl/tasks"), [name: "Visible"], Map)

        when:
        def response = http.getForEntity(url("/api/kanban/boards/ctl/ascii?width=80&style=compact"), String)

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getFirst("Content-Type").startsWith(MediaType.TEXT_PLAIN_VALUE)
        response.body.contains("Controller Board")
        response.body.contains("Visible")
    }

    def "GET /boards/{id}/snapshot for unknown board returns 404"() {
        when:
        def response = http.getForEntity(url("/api/kanban/boards/nope/snapshot"), Map)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        response.body.error.toString().contains("nope")
    }

    def "GET /tasks/{id} for unknown id returns 404"() {
        when:
        def response = http.getForEntity(url("/api/kanban/tasks/nope-id"), Map)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
    }

    def "POST /boards rejects a malformed body"() {
        when:
        def response = http.postForEntity(url("/api/kanban/boards"),
                [id: "", initialState: "x"], Map)

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "POST /tasks/{id}/transition rejects a missing event"() {
        given:
        registerFixtureBoard()
        def card = http.postForEntity(url("/api/kanban/boards/ctl/tasks"),
                [name: "Missing"], Map).body

        when:
        def response = http.postForEntity(url("/api/kanban/tasks/${card.id}/transition"),
                [:], Map)

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def "DELETE /boards/{id} removes the board"() {
        given:
        registerFixtureBoard()

        when:
        def headers = new HttpHeaders()
        def delete = http.exchange(url("/api/kanban/boards/ctl"),
                HttpMethod.DELETE, new HttpEntity<Void>(headers), Void)

        then:
        delete.statusCode == HttpStatus.NO_CONTENT

        and:
        http.getForEntity(url("/api/kanban/boards/ctl"), Map).statusCode == HttpStatus.NOT_FOUND
        !java.nio.file.Files.exists(tempDir.resolve("boards/ctl.yaml"))
    }

    @SpringBootApplication
    static class TestApp {
        // Provide a TaskStore directly so KanbanAutoConfiguration's
        // @ConditionalOnBean(TaskStore.class) gate fires without bringing in
        // jaiclaw-tasks' full auto-config (which itself requires a ToolRegistry).
        // Method name disambiguated from the read-only spec's TestApp to avoid
        // BeanDefinitionOverrideException when both sit in the same package.
        @Bean
        TaskStore writableControllerSpecTaskStore() {
            Path dir = Files.createTempDirectory("kanban-ctl-test-tasks")
            new JsonFileTaskStore(dir, new TenantGuard(TenantProperties.DEFAULT))
        }
    }
}
