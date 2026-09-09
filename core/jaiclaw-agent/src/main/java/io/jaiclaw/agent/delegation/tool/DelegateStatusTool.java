package io.jaiclaw.agent.delegation.tool;

import io.jaiclaw.agent.delegation.SubAgentHandle;
import io.jaiclaw.agent.delegation.SubAgentLauncher;
import io.jaiclaw.agent.delegation.SubAgentResult;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code delegate_status} — polls, collects or cancels a subagent started with
 * {@code delegate_task}.
 *
 * <p>Actions:
 * <ul>
 *   <li>{@code status} (default) — non-blocking snapshot</li>
 *   <li>{@code result} — the terminal result if finished, otherwise the same
 *       snapshot; never blocks, so a parent cannot deadlock on its own child</li>
 *   <li>{@code cancel} — request cancellation</li>
 * </ul>
 *
 * <p>Phase 2 of the 1.2.0 plan.
 */
public class DelegateStatusTool extends AbstractBuiltinTool {

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "handle_id": {
                  "type": "string",
                  "description": "The handle_id returned by delegate_task."
                },
                "action": {
                  "type": "string",
                  "enum": ["status", "result", "cancel"],
                  "description": "status = check progress (default); result = fetch the answer if finished; cancel = stop the subagent."
                }
              },
              "required": ["handle_id"]
            }
            """;

    private final SubAgentLauncher launcher;

    public DelegateStatusTool(SubAgentLauncher launcher) {
        super(new ToolDefinition(
                "delegate_status",
                "Check on, collect, or cancel a subagent previously started with delegate_task. "
                        + "Never blocks — if the subagent is still running you get a RUNNING snapshot.",
                DelegateTaskTool.SECTION,
                SCHEMA,
                Set.of(ToolProfile.FULL, ToolProfile.CODING)));
        this.launcher = launcher;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String handleId = optionalParam(parameters, "handle_id", null);
        if (handleId == null || handleId.isBlank()) {
            return new ToolResult.Error("'handle_id' is required — use the value returned by delegate_task.");
        }
        String action = optionalParam(parameters, "action", "status").trim().toLowerCase(Locale.ROOT);

        Optional<SubAgentHandle> found = launcher.find(handleId);
        if (found.isEmpty()) {
            return new ToolResult.Error("No subagent found with handle_id '" + handleId
                    + "'. It may never have started, or the process restarted since it ran.");
        }
        SubAgentHandle handle = found.get();

        return switch (action) {
            case "cancel" -> {
                boolean cancelled = launcher.cancel(handleId);
                yield new ToolResult.Success("""
                        {"handle_id":"%s","cancelled":%s}"""
                        .formatted(handleId, cancelled));
            }
            case "status", "result" -> {
                SubAgentResult r = handle.poll();
                yield new ToolResult.Success(render(r, handleId));
            }
            default -> new ToolResult.Error(
                    "Unknown action '" + action + "'. Use status, result, or cancel.");
        };
    }

    private String render(SubAgentResult r, String handleId) {
        return """
                {"handle_id":"%s","status":"%s","session_key":%s,"summary":%s,"error":%s}"""
                .formatted(handleId, r.status(),
                        DelegateTaskTool.quote(r.sessionKey()),
                        DelegateTaskTool.quote(r.summary()),
                        DelegateTaskTool.quote(r.error()));
    }
}
