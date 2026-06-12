package io.jaiclaw.kanban.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskStore;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Agent tool {@code task_claim} — sets the {@code assignee} on a card via
 * {@link TaskStore#compareAndSave}. The card's state and column are
 * unchanged; only the assignee field is updated.
 */
public class TaskClaimTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "taskId":   { "type": "string", "description": "Card / task id" },
                "assignee": { "type": "string", "description": "Assignee handle, or empty/null to unassign" }
              },
              "required": ["taskId"]
            }""";

    private final TaskStore taskStore;

    public TaskClaimTool(TaskStore taskStore) {
        super(new ToolDefinition("task_claim",
                "Claim a kanban card by setting its assignee",
                ToolCatalog.SECTION_KANBAN, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.taskStore = taskStore;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String taskId = requireParam(parameters, "taskId");
        String assignee = optionalParam(parameters, "assignee", null);
        if (assignee != null && assignee.isBlank()) assignee = null;
        Optional<TaskRecord> existing = taskStore.findById(taskId);
        if (existing.isEmpty()) {
            return new ToolResult.Error("Task not found: " + taskId);
        }
        TaskRecord claimed = existing.get().withAssignee(assignee);
        Optional<TaskRecord> persisted = taskStore.compareAndSave(claimed);
        if (persisted.isEmpty()) {
            return new ToolResult.Error(
                    "Concurrent modification on task " + taskId + " — re-read and retry");
        }
        return new ToolResult.Success(
                assignee == null
                        ? "Card " + taskId + " unassigned"
                        : "Card " + taskId + " claimed by " + assignee);
    }
}
