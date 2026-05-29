package io.jaiclaw.tasks.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskService;
import io.jaiclaw.tasks.TaskStatus;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ListTasksTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "status": { "type": "string", "description": "Filter by status: QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, BLOCKED (optional)" }
              }
            }""";

    private final TaskService service;

    public ListTasksTool(TaskService service) {
        super(new ToolDefinition("task_list", "List tasks, optionally filtered by status",
                ToolCatalog.SECTION_TASKS, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.service = service;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String statusStr = optionalParam(parameters, "status", null);
        List<TaskRecord> tasks;

        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                TaskStatus status = TaskStatus.valueOf(statusStr.toUpperCase());
                tasks = service.listByStatus(status);
            } catch (IllegalArgumentException e) {
                return new ToolResult.Error("Invalid status: " + statusStr);
            }
        } else {
            tasks = service.listTasks();
        }

        if (tasks.isEmpty()) {
            return new ToolResult.Success("No tasks found");
        }

        var sb = new StringBuilder();
        sb.append("Tasks (").append(tasks.size()).append("):\n");
        for (TaskRecord t : tasks) {
            sb.append("- **").append(t.name()).append("** (id: ").append(t.id()).append(") [")
                    .append(t.status()).append("]\n");
        }
        return new ToolResult.Success(sb.toString());
    }
}
