package io.jaiclaw.tasks.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tasks.TaskService;
import io.jaiclaw.tasks.TaskStatus;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Set;

public class UpdateTaskTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "id": { "type": "string", "description": "Task ID" },
                "status": { "type": "string", "description": "New status: QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, BLOCKED" },
                "result": { "type": "string", "description": "Task result (sets status to SUCCEEDED)" }
              },
              "required": ["id"]
            }""";

    private final TaskService service;

    public UpdateTaskTool(TaskService service) {
        super(new ToolDefinition("task_update", "Update task status or result",
                ToolCatalog.SECTION_TASKS, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.service = service;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String id = requireParam(parameters, "id");
        String result = optionalParam(parameters, "result", null);
        String statusStr = optionalParam(parameters, "status", null);

        if (result != null && !result.isEmpty()) {
            return service.completeTask(id, result)
                    .map(t -> (ToolResult) new ToolResult.Success("Task completed: " + t.name()))
                    .orElse(new ToolResult.Error("Task not found: " + id));
        }

        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                TaskStatus status = TaskStatus.valueOf(statusStr.toUpperCase());
                return service.updateStatus(id, status)
                        .map(t -> (ToolResult) new ToolResult.Success(
                                "Task updated: " + t.name() + " → " + t.status()))
                        .orElse(new ToolResult.Error("Task not found: " + id));
            } catch (IllegalArgumentException e) {
                return new ToolResult.Error("Invalid status: " + statusStr);
            }
        }

        return new ToolResult.Error("Provide 'status' or 'result' to update");
    }
}
