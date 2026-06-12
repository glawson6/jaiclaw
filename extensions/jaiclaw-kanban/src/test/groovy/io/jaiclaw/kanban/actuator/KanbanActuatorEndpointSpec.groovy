package io.jaiclaw.kanban.actuator

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.KanbanProperties
import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.TerminalKind
import io.jaiclaw.kanban.model.TransitionDefinition
import io.jaiclaw.kanban.model.TransitionRecord
import io.jaiclaw.kanban.service.KanbanBoardService
import io.jaiclaw.kanban.service.TransitionHistory
import io.jaiclaw.tasks.TaskStatus
import spock.lang.Specification

import java.time.Instant

class KanbanActuatorEndpointSpec extends Specification {

    TenantGuard guard = new TenantGuard(TenantProperties.DEFAULT)
    KanbanBoardService boardService = new KanbanBoardService(guard)
    TransitionHistory history = new TransitionHistory(50)
    KanbanProperties props = new KanbanProperties(true, "/tmp/kanban-boards", null, null, null, null, null, null, null, null)
    KanbanActuatorEndpoint endpoint = new KanbanActuatorEndpoint(boardService, history, props)

    def setup() {
        boardService.cache(new BoardDefinition("ax", "Actuator Board", [], "backlog", [
                new ColumnDefinition("backlog", "Backlog", TaskStatus.QUEUED, null, false, null, null),
                new ColumnDefinition("done", "Done", TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [new TransitionDefinition("backlog", "done", "FINISH", [:])]))
    }

    def "list() returns engine info and a board summary"() {
        when:
        def out = endpoint.list()

        then:
        out.engine == "graph"
        out.boardsDir.contains("kanban-boards")
        out.writable == true
        out.count == 1
        (out.boards as List)[0].id == "ax"
        (out.boards as List)[0].columnCount == 2
    }

    def "byId() returns a definition summary and recent transitions"() {
        given:
        history.record(new TransitionRecord("t1", "ax", "backlog", "done",
                "FINISH", "alice", "default", Instant.now()))

        when:
        def out = endpoint.byId("ax")

        then:
        (out.definition as Map).id == "ax"
        (out.recentTransitions as List).size() == 1
        ((out.recentTransitions as List)[0] as Map).event == "FINISH"
    }

    def "byId() for unknown board returns an error map"() {
        when:
        def out = endpoint.byId("missing")

        then:
        (out as Map).containsKey("error")
    }
}
