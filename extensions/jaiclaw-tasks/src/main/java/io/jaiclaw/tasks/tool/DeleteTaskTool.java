package io.jaiclaw.tasks.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tasks.TaskService;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Set;

public class DeleteTaskTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "id": { "type": "string", "description": "Task ID to delete" }
              },
              "required": ["id"]
            }""";

    private final TaskService service;

    public DeleteTaskTool(TaskService service) {
        super(new ToolDefinition("task_delete", "Delete a task by ID",
                ToolCatalog.SECTION_TASKS, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.service = service;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String id = requireParam(parameters, "id");
        if (service.deleteTask(id)) {
            return new ToolResult.Success("Deleted task: " + id);
        }
        return new ToolResult.Error("Task not found: " + id);
    }
}
