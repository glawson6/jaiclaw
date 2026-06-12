package io.jaiclaw.kanban.actuator;

import io.jaiclaw.kanban.KanbanProperties;
import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.model.TransitionRecord;
import io.jaiclaw.kanban.service.KanbanBoardService;
import io.jaiclaw.kanban.service.TransitionHistory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring Boot Actuator endpoint at {@code /actuator/kanban}. Mirrors
 * {@code PipelineActuatorEndpoint} from {@code jaiclaw-pipeline}: a
 * read-only view of registered boards and recent transitions for
 * operator visibility.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /actuator/kanban} — engine info + board summaries</li>
 *   <li>{@code GET /actuator/kanban/{boardId}} — definition + recent transitions</li>
 * </ul>
 */
@Endpoint(id = "kanban")
public class KanbanActuatorEndpoint {

    private final KanbanBoardService boardService;
    private final TransitionHistory history;
    private final KanbanProperties properties;

    public KanbanActuatorEndpoint(KanbanBoardService boardService,
                                  TransitionHistory history,
                                  KanbanProperties properties) {
        this.boardService = boardService;
        this.history = history;
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> list() {
        List<BoardDefinition> all = boardService.listAllUnscoped();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engine", properties.engine().name());
        out.put("boardsDir", properties.boardsDir());
        out.put("writable", properties.boards().writable());
        out.put("count", all.size());
        out.put("boards", all.stream().map(KanbanActuatorEndpoint::summarize).toList());
        return out;
    }

    @ReadOperation
    public Map<String, Object> byId(@Selector String boardId) {
        Optional<BoardDefinition> definition = boardService.listAllUnscoped().stream()
                .filter(b -> b.id().equals(boardId)).findFirst();
        if (definition.isEmpty()) {
            return Map.of("error", "Board '" + boardId + "' not found");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("definition", summarize(definition.get()));
        out.put("recentTransitions", history.forBoard(boardId, 50).stream()
                .map(KanbanActuatorEndpoint::summarize).toList());
        return out;
    }

    private static Map<String, Object> summarize(BoardDefinition definition) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", definition.id());
        map.put("name", definition.name());
        map.put("initialState", definition.initialState());
        map.put("columnCount", definition.columns().size());
        map.put("transitionCount", definition.transitions().size());
        map.put("tenantIds", definition.tenantIds());
        map.put("columns", definition.columns().stream()
                .map(c -> {
                    Map<String, Object> col = new LinkedHashMap<>();
                    col.put("state", c.state());
                    col.put("name", c.name());
                    col.put("phase", c.phase() != null ? c.phase().name() : null);
                    col.put("wipLimit", c.wipLimit());
                    col.put("terminal", c.terminal());
                    return col;
                }).toList());
        return map;
    }

    private static Map<String, Object> summarize(TransitionRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", record.taskId());
        map.put("boardId", record.boardId());
        map.put("fromState", record.fromState());
        map.put("toState", record.toState());
        map.put("event", record.event());
        map.put("actor", record.actor());
        map.put("tenantId", record.tenantId());
        map.put("timestamp", record.timestamp());
        return map;
    }
}
