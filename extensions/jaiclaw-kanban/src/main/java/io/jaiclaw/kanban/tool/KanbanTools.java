package io.jaiclaw.kanban.tool;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.kanban.render.BoardAsciiRenderer;
import io.jaiclaw.kanban.service.BoardSnapshotService;
import io.jaiclaw.kanban.service.KanbanBoardService;
import io.jaiclaw.kanban.service.TaskTransitionService;
import io.jaiclaw.tasks.TaskStore;
import io.jaiclaw.tools.ToolRegistry;

import java.util.List;

/**
 * Factory + registrar for the kanban agent tools.
 */
public final class KanbanTools {

    private KanbanTools() {}

    public static List<ToolCallback> all(
            KanbanBoardService boardService,
            BoardSnapshotService snapshotService,
            TaskTransitionService transitionService,
            TaskStore taskStore,
            BoardAsciiRenderer renderer) {
        return List.of(
                new BoardListTool(boardService),
                new BoardShowTool(snapshotService),
                new BoardAsciiTool(snapshotService, renderer),
                new TaskMoveTool(transitionService),
                new TaskClaimTool(taskStore)
        );
    }

    public static void registerAll(
            ToolRegistry registry,
            KanbanBoardService boardService,
            BoardSnapshotService snapshotService,
            TaskTransitionService transitionService,
            TaskStore taskStore,
            BoardAsciiRenderer renderer) {
        all(boardService, snapshotService, transitionService, taskStore, renderer)
                .forEach(registry::register);
    }
}
