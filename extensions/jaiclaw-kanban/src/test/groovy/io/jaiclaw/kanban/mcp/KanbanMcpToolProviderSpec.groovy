package io.jaiclaw.kanban.mcp

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.events.KanbanHookFirer
import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.TerminalKind
import io.jaiclaw.kanban.model.TransitionDefinition
import io.jaiclaw.kanban.render.BoardAsciiRenderer
import io.jaiclaw.kanban.service.BoardSnapshotService
import io.jaiclaw.kanban.service.KanbanBoardService
import io.jaiclaw.kanban.service.TaskTransitionService
import io.jaiclaw.kanban.service.TransitionHistory
import io.jaiclaw.kanban.state.TransitionGraphStateEngine
import io.jaiclaw.tasks.JsonFileTaskStore
import io.jaiclaw.tasks.TaskStatus
import io.jaiclaw.tasks.TaskStore
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class KanbanMcpToolProviderSpec extends Specification {

    @TempDir
    Path tempDir

    TenantGuard tenantGuard = new TenantGuard(TenantProperties.DEFAULT)
    TaskStore taskStore
    KanbanBoardService boardService
    BoardSnapshotService snapshotService
    TaskTransitionService transitionService
    BoardAsciiRenderer renderer = new BoardAsciiRenderer()
    KanbanMcpToolProvider provider

    def setup() {
        taskStore = new JsonFileTaskStore(tempDir, tenantGuard)
        def engine = new TransitionGraphStateEngine()
        boardService = new KanbanBoardService(tenantGuard)
        snapshotService = new BoardSnapshotService(boardService, taskStore, engine)
        def publisher = { Object _ignored -> } as ApplicationEventPublisher
        transitionService = new TaskTransitionService(
                taskStore, boardService, engine, new TransitionHistory(20),
                publisher, new KanbanHookFirer(null), tenantGuard)
        boardService.cache(new BoardDefinition("mcp", "MCP Board", [], "backlog", [
                new ColumnDefinition("backlog", "Backlog", TaskStatus.QUEUED, null, false, null, null),
                new ColumnDefinition("done", "Done", TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [new TransitionDefinition("backlog", "done", "FINISH", [:])]))
        provider = new KanbanMcpToolProvider(boardService, snapshotService,
                transitionService, taskStore, renderer)
    }

    def "server identity"() {
        expect:
        provider.getServerName() == "kanban"
        provider.getServerDescription().toLowerCase().contains("kanban")
    }

    def "getTools returns the five expected tool definitions"() {
        when:
        def tools = provider.getTools()

        then:
        tools*.name() as Set == [
                "board_list", "board_show", "board_ascii", "task_move", "task_claim"] as Set
        tools.every { it.inputSchema().contains("type") }
    }

    def "board_list returns count + boards in JSON"() {
        when:
        def result = provider.execute("board_list", [:], null)

        then:
        !result.isError()
        result.content().contains("\"count\":1")
        result.content().contains("\"id\":\"mcp\"")
    }

    def "board_show returns a snapshot JSON"() {
        when:
        def result = provider.execute("board_show", [boardId: "mcp"], null)

        then:
        !result.isError()
        result.content().contains("\"boardId\":\"mcp\"")
        result.content().contains("\"backlog\"")
    }

    def "board_ascii returns rendered text"() {
        when:
        def result = provider.execute("board_ascii",
                [boardId: "mcp", width: 60, style: "compact"], null)

        then:
        !result.isError()
        result.content().contains("MCP Board")
    }

    def "task_move advances a card and returns JSON"() {
        given:
        def card = transitionService.createCard("mcp", "Mover", null, [:])

        when:
        def result = provider.execute("task_move",
                [taskId: card.id(), event: "FINISH", actor: "alice"], null)

        then:
        !result.isError()
        result.content().contains("\"toState\":\"done\"")
        result.content().contains("\"event\":\"FINISH\"")
    }

    def "task_move surfaces engine rejection as an MCP error"() {
        given:
        def card = transitionService.createCard("mcp", "Nope", null, [:])

        when:
        def result = provider.execute("task_move",
                [taskId: card.id(), event: "BOGUS"], null)

        then:
        result.isError()
        result.content().contains("Transition rejected")
    }

    def "task_claim sets the assignee and bumps version"() {
        given:
        def card = transitionService.createCard("mcp", "Claim me", null, [:])

        when:
        def result = provider.execute("task_claim",
                [taskId: card.id(), assignee: "bob"], null)

        then:
        !result.isError()
        result.content().contains("\"assignee\":\"bob\"")
        result.content().contains("\"version\":1")
    }

    def "unknown tool returns an MCP error"() {
        when:
        def result = provider.execute("nope", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }

    def "missing required parameter is reported"() {
        when:
        def result = provider.execute("board_show", [:], null)

        then:
        result.isError()
        result.content().toLowerCase().contains("missing")
    }
}
