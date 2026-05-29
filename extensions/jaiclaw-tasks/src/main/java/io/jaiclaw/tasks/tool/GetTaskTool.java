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

public class GetTaskTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "id": { "type": "string", "description": "Task ID" }
              },
              "required": ["id"]
            }""";

    private final TaskService service;

    public GetTaskTool(TaskService service) {
        super(new ToolDefinition("task_get", "Get task details by ID",
                ToolCatalog.SECTION_TASKS, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.service = service;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String id = requireParam(parameters, "id");
        return service.getTask(id)
                .map(task -> {
                    var sb = new StringBuilder();
                    sb.append("**").append(task.name()).append("** (").append(task.id()).append(")\n");
                    sb.append("Status: ").append(task.status()).append("\n");
                    if (task.description() != null && !task.description().isEmpty()) {
                        sb.append("Description: ").append(task.description()).append("\n");
                    }
                    if (task.result() != null) {
                        sb.append("Result: ").append(task.result()).append("\n");
                    }
                    if (task.error() != null) {
                        sb.append("Error: ").append(task.error()).append("\n");
                    }
                    sb.append("Created: ").append(task.createdAt());
                    return (ToolResult) new ToolResult.Success(sb.toString());
                })
                .orElse(new ToolResult.Error("Task not found: " + id));
    }
}
