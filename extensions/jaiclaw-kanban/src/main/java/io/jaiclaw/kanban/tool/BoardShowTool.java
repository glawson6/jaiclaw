package io.jaiclaw.kanban.tool;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.kanban.model.BoardSnapshot;
import io.jaiclaw.kanban.service.BoardSnapshotService;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Agent tool {@code board_show} — returns the current
 * {@link BoardSnapshot} for a board as JSON. The same shape the REST
 * {@code GET /boards/{id}/snapshot} endpoint produces.
 */
public class BoardShowTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "boardId": { "type": "string", "description": "Board id" }
              },
              "required": ["boardId"]
            }""";

    private static final ObjectMapper JSON = new ObjectMapper()
            
            ;

    private final BoardSnapshotService snapshotService;

    public BoardShowTool(BoardSnapshotService snapshotService) {
        super(new ToolDefinition("board_show",
                "Show a kanban board snapshot (columns and cards) as JSON",
                ToolCatalog.SECTION_KANBAN, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.snapshotService = snapshotService;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context)
            throws JacksonException {
        String boardId = requireParam(parameters, "boardId");
        Optional<BoardSnapshot> snapshot = snapshotService.snapshot(boardId);
        if (snapshot.isEmpty()) {
            return new ToolResult.Error("Board not found: " + boardId);
        }
        return new ToolResult.Success(JSON.writeValueAsString(snapshot.get()));
    }
}
