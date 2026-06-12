package io.jaiclaw.kanban.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.service.KanbanBoardService;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent tool {@code board_list} — returns the kanban boards visible to
 * the current tenant as a short summary line. The agent can then drill
 * into a specific board via {@code board_show} or {@code board_ascii}.
 */
public class BoardListTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {}
            }""";

    private final KanbanBoardService boardService;

    public BoardListTool(KanbanBoardService boardService) {
        super(new ToolDefinition("board_list", "List kanban boards visible to the current tenant",
                ToolCatalog.SECTION_KANBAN, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.boardService = boardService;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        List<BoardDefinition> boards = boardService.list();
        if (boards.isEmpty()) {
            return new ToolResult.Success("No kanban boards available.");
        }
        String body = boards.stream()
                .map(b -> "- " + b.id() + " (" + b.name() + "): "
                        + b.columns().size() + " columns, "
                        + b.transitions().size() + " transitions, initial=" + b.initialState())
                .collect(Collectors.joining("\n"));
        return new ToolResult.Success(boards.size() + " board(s):\n" + body);
    }
}
