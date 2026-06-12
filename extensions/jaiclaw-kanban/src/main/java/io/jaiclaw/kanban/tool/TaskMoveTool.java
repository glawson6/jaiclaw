package io.jaiclaw.kanban.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.kanban.service.TaskTransitionService;
import io.jaiclaw.kanban.state.TransitionResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Set;

/**
 * Agent tool {@code task_move} — fires a transition event on a kanban
 * card. Returns the accepted transition (from/to states) or the rejection
 * reason — same accept/reject vocabulary as the REST
 * {@code POST /tasks/{id}/transition} endpoint and the
 * {@link TaskTransitionService}.
 */
public class TaskMoveTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "taskId": { "type": "string", "description": "Card / task id" },
                "event":  { "type": "string", "description": "Transition event name (e.g. START, APPROVE)" },
                "actor":  { "type": "string", "description": "Optional actor name for the audit record" }
              },
              "required": ["taskId", "event"]
            }""";

    private final TaskTransitionService transitionService;

    public TaskMoveTool(TaskTransitionService transitionService) {
        super(new ToolDefinition("task_move",
                "Move a kanban card by firing a transition event",
                ToolCatalog.SECTION_KANBAN, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.transitionService = transitionService;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String taskId = requireParam(parameters, "taskId");
        String event = requireParam(parameters, "event");
        String actor = optionalParam(parameters, "actor", null);
        try {
            TransitionResult result = transitionService.transition(taskId, event, actor);
            if (!result.accepted()) {
                return new ToolResult.Error(
                        "Transition rejected: " + result.reason() + " (current state: " + result.fromState() + ")");
            }
            return new ToolResult.Success(
                    "Card " + taskId + " moved " + result.fromState() + " → " + result.toState()
                            + " via " + event);
        } catch (IllegalArgumentException ex) {
            return new ToolResult.Error(ex.getMessage());
        }
    }
}
