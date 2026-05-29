package io.jaiclaw.tasks;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tasks.tool.*;
import io.jaiclaw.tools.ToolRegistry;

import java.util.List;

/**
 * Factory for task management tools.
 */
public final class TaskTools {

    private TaskTools() {}

    public static List<ToolCallback> all(TaskService service) {
        return List.of(
                new CreateTaskTool(service),
                new ListTasksTool(service),
                new GetTaskTool(service),
                new UpdateTaskTool(service),
                new DeleteTaskTool(service)
        );
    }

    public static void registerAll(ToolRegistry registry, TaskService service) {
        all(service).forEach(registry::register);
    }
}
