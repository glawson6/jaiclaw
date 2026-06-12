package io.jaiclaw.kanban.tool

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
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

/**
 * Coverage for the five kanban agent tools — they all wire over the same
 * services, so one spec for all keeps the file count sane.
 */
class KanbanToolsSpec extends Specification {

    @TempDir
    Path tempDir

    TenantGuard tenantGuard = new TenantGuard(TenantProperties.DEFAULT)
    TaskStore taskStore
    KanbanBoardService boardService
    BoardSnapshotService snapshotService
    TaskTransitionService transitionService
    BoardAsciiRenderer renderer = new BoardAsciiRenderer()

    def setup() {
        taskStore = new JsonFileTaskStore(tempDir, tenantGuard)
        def engine = new TransitionGraphStateEngine()
        boardService = new KanbanBoardService(tenantGuard)
        snapshotService = new BoardSnapshotService(boardService, taskStore, engine)
        def publisher = { Object _ignored -> } as ApplicationEventPublisher
        transitionService = new TaskTransitionService(
                taskStore, boardService, engine, new TransitionHistory(20),
                publisher, new KanbanHookFirer(null), tenantGuard)

        // Register the fixture board for every spec.
        def board = new BoardDefinition("tools", "Tools Board", [], "backlog", [
                new ColumnDefinition("backlog",  "Backlog",  TaskStatus.QUEUED,    null, false, null, null),
                new ColumnDefinition("drafting", "Drafting", TaskStatus.RUNNING,   2,    false, null, null),
                new ColumnDefinition("done",     "Done",     TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [
                new TransitionDefinition("backlog",  "drafting", "START",  [:]),
                new TransitionDefinition("drafting", "done",     "FINISH", [:]),
        ])
        boardService.cache(board)
    }

    def "board_list returns visible boards"() {
        when:
        def result = new BoardListTool(boardService).execute([:], (ToolContext) null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains("tools")
        ((ToolResult.Success) result).content().contains("Tools Board")
    }

    def "board_show returns a JSON snapshot"() {
        when:
        def result = new BoardShowTool(snapshotService).execute([boardId: "tools"], (ToolContext) null)

        then:
        result instanceof ToolResult.Success
        def json = ((ToolResult.Success) result).content()
        json.contains("\"boardId\":\"tools\"")
        json.contains("\"backlog\"")
        json.contains("\"done\"")
    }

    def "board_show errors on unknown board"() {
        when:
        def result = new BoardShowTool(snapshotService).execute([boardId: "nope"], (ToolContext) null)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("nope")
    }

    def "board_ascii returns the rendered text"() {
        given:
        transitionService.createCard("tools", "Card One", "desc 1", [:])

        when:
        def result = new BoardAsciiTool(snapshotService, renderer)
                .execute([boardId: "tools", width: 80, style: "compact"], (ToolContext) null)

        then:
        result instanceof ToolResult.Success
        def out = ((ToolResult.Success) result).content()
        out.contains("Tools Board")
        out.contains("Card One")
    }

    def "task_move accepts a legal transition"() {
        given:
        def card = transitionService.createCard("tools", "Mover", null, [:])

        when:
        def result = new TaskMoveTool(transitionService)
                .execute([taskId: card.id(), event: "START"], (ToolContext) null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains("backlog → drafting")
    }

    def "task_move rejects an illegal transition with the engine's reason"() {
        given:
        def card = transitionService.createCard("tools", "Wrong", null, [:])

        when:
        def result = new TaskMoveTool(transitionService)
                .execute([taskId: card.id(), event: "DOES_NOT_EXIST"], (ToolContext) null)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("no transition 'DOES_NOT_EXIST'")
    }

    def "task_move errors on unknown task"() {
        when:
        def result = new TaskMoveTool(transitionService)
                .execute([taskId: "ghost", event: "START"], (ToolContext) null)

        then:
        result instanceof ToolResult.Error
    }

    def "task_claim sets the assignee"() {
        given:
        def card = transitionService.createCard("tools", "Claimable", null, [:])

        when:
        def result = new TaskClaimTool(taskStore)
                .execute([taskId: card.id(), assignee: "alice"], (ToolContext) null)

        then:
        result instanceof ToolResult.Success
        taskStore.findById(card.id()).get().assignee() == "alice"
    }

    def "task_claim with an empty assignee unassigns the card"() {
        given:
        def card = transitionService.createCard("tools", "Unassign me", null, [:])
        new TaskClaimTool(taskStore).execute([taskId: card.id(), assignee: "bob"], (ToolContext) null)

        when:
        new TaskClaimTool(taskStore).execute([taskId: card.id(), assignee: ""], (ToolContext) null)

        then:
        taskStore.findById(card.id()).get().assignee() == null
    }

    def "KanbanTools.all registers exactly five callbacks"() {
        when:
        def tools = KanbanTools.all(boardService, snapshotService, transitionService,
                taskStore, renderer)

        then:
        tools.size() == 5
        tools*.definition()*.name() as Set == [
                "board_list", "board_show", "board_ascii", "task_move", "task_claim"] as Set
    }
}
