package io.jaiclaw.kanban.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.kanban.model.BoardSnapshot;
import io.jaiclaw.kanban.render.AsciiBoardOptions;
import io.jaiclaw.kanban.render.BoardAsciiRenderer;
import io.jaiclaw.kanban.service.BoardSnapshotService;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Agent tool {@code board_ascii} — renders the kanban board as ASCII art
 * (FULL or COMPACT). Equivalent to the REST {@code GET /boards/{id}/ascii}
 * endpoint, available so an agent can print its own board into any chat
 * channel without going through HTTP.
 */
public class BoardAsciiTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "boardId": { "type": "string", "description": "Board id" },
                "width":   { "type": "integer", "description": "Canvas width in columns (default 120)" },
                "style":   { "type": "string", "description": "full or compact (default full)" }
              },
              "required": ["boardId"]
            }""";

    private final BoardSnapshotService snapshotService;
    private final BoardAsciiRenderer renderer;

    public BoardAsciiTool(BoardSnapshotService snapshotService, BoardAsciiRenderer renderer) {
        super(new ToolDefinition("board_ascii",
                "Render a kanban board as ASCII (FULL boxed view or COMPACT table)",
                ToolCatalog.SECTION_KANBAN, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.snapshotService = snapshotService;
        this.renderer = renderer;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String boardId = requireParam(parameters, "boardId");
        int width = parseIntOrDefault(parameters.get("width"), 120);
        String styleStr = optionalParam(parameters, "style", "full");
        AsciiBoardOptions.Style style = "compact".equalsIgnoreCase(styleStr)
                ? AsciiBoardOptions.Style.COMPACT
                : AsciiBoardOptions.Style.FULL;
        Optional<BoardSnapshot> snapshot = snapshotService.snapshot(boardId);
        if (snapshot.isEmpty()) {
            return new ToolResult.Error("Board not found: " + boardId);
        }
        AsciiBoardOptions options = new AsciiBoardOptions(width, style, 2, true, "(empty)");
        return new ToolResult.Success(renderer.render(snapshot.get(), options));
    }

    private static int parseIntOrDefault(Object raw, int fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number n) return n.intValue();
        try { return Integer.parseInt(raw.toString()); }
        catch (NumberFormatException e) { return fallback; }
    }
}
