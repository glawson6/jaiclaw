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

public class CreateTaskTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "name": { "type": "string", "description": "Task name" },
                "description": { "type": "string", "description": "Task description or instructions" }
              },
              "required": ["name"]
            }""";

    private final TaskService service;

    public CreateTaskTool(TaskService service) {
        super(new ToolDefinition("task_create", "Create a new task",
                ToolCatalog.SECTION_TASKS, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.service = service;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String name = requireParam(parameters, "name");
        String description = optionalParam(parameters, "description", "");
        var task = service.createTask(name, description, null);
        return new ToolResult.Success("Created task: " + task.name() + " (id: " + task.id() + ")");
    }
}
