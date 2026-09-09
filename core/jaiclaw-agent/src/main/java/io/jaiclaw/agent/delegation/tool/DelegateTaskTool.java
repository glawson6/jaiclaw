package io.jaiclaw.agent.delegation.tool;

import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.AgentRuntimeContext;
import io.jaiclaw.agent.delegation.SubAgentHandle;
import io.jaiclaw.agent.delegation.SubAgentLauncher;
import io.jaiclaw.agent.delegation.SubAgentRequest;
import io.jaiclaw.agent.delegation.SubAgentResult;
import io.jaiclaw.config.DelegationProperties;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Set;

/**
 * {@code delegate_task} — hands a bounded subtask to a child agent.
 *
 * <p>The child gets its own session, its own iteration budget and a tool profile
 * no wider than the caller's. By default the call blocks for the result; with
 * {@code wait=false} it returns a handle the parent polls via
 * {@code delegate_status}.
 *
 * <p>Registered only when {@code jaiclaw.agent.delegation.enabled=true}. Lives in
 * {@code jaiclaw-agent} rather than {@code jaiclaw-tools} because it needs
 * {@link SubAgentLauncher} and {@link AgentRuntimeContext}, and
 * {@code jaiclaw-agent} already depends on {@code jaiclaw-tools} — the reverse
 * would be a module cycle.
 *
 * <p>Phase 2 of the 1.2.0 plan.
 */
public class DelegateTaskTool extends AbstractBuiltinTool {

    /** Tool section, so adopters can exclude the whole delegation surface by policy. */
    public static final String SECTION = "delegation";

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "goal": {
                  "type": "string",
                  "description": "What the subagent should accomplish. Be specific and self-contained — the subagent does not see this conversation."
                },
                "context": {
                  "type": "string",
                  "description": "Optional background the subagent needs: file paths, prior findings, constraints."
                },
                "tool_profile": {
                  "type": "string",
                  "enum": ["NONE", "MINIMAL", "MESSAGING", "CODING", "FULL"],
                  "description": "Tool access for the subagent. Narrowed to your own profile if wider. Prefer the narrowest that can do the job."
                },
                "max_iterations": {
                  "type": "integer",
                  "description": "Tool-loop budget for the subagent. Omit to use the configured default."
                },
                "wait": {
                  "type": "boolean",
                  "description": "Block for the result (default true). Pass false to run it in the background and poll with delegate_status."
                }
              },
              "required": ["goal"]
            }
            """;

    private final SubAgentLauncher launcher;
    private final DelegationProperties properties;

    public DelegateTaskTool(SubAgentLauncher launcher, DelegationProperties properties) {
        super(new ToolDefinition(
                "delegate_task",
                "Delegate a self-contained subtask to a subagent with its own session, "
                        + "budget and (narrower) tool access. Use for work that is separable from "
                        + "the main thread — research, a focused analysis, a bounded search. "
                        + "The subagent cannot see this conversation, so state the goal in full.",
                SECTION,
                SCHEMA,
                Set.of(ToolProfile.FULL, ToolProfile.CODING)));
        this.launcher = launcher;
        this.properties = properties;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String goal = optionalParam(parameters, "goal", null);
        if (goal == null || goal.isBlank()) {
            return new ToolResult.Error("'goal' is required and must describe what the subagent should do.");
        }

        AgentRuntimeContext parent = parentContext(context);
        if (parent == null) {
            // Without the parent context we cannot enforce depth, tenant or profile
            // narrowing — refuse rather than silently delegating unbounded work.
            return new ToolResult.Error(
                    "Delegation is unavailable: no agent runtime context is attached to this call.");
        }

        ToolProfile requested = parseProfile(optionalParam(parameters, "tool_profile", null));
        int maxIterations = parseInt(parameters.get("max_iterations"));
        boolean wait = parseBool(parameters.get("wait"), true);

        SubAgentRequest request = new SubAgentRequest(
                goal,
                optionalParam(parameters, "context", null),
                parent,
                requested,
                maxIterations,
                wait);

        SubAgentHandle handle = launcher.launch(request);
        SubAgentResult result = wait
                ? handle.await(properties.waitTimeout())
                : handle.poll();

        return switch (result.status()) {
            // A refusal is a fact the model should reason about, not an exception.
            case REFUSED -> new ToolResult.Error(result.error());
            case FAILED -> new ToolResult.Error("Subagent failed: " + result.error());
            case CANCELLED -> new ToolResult.Error("Subagent was cancelled before it finished.");
            case COMPLETED -> new ToolResult.Success(render(result));
            case RUNNING -> new ToolResult.Success(renderRunning(result, wait));
        };
    }

    private String render(SubAgentResult r) {
        return """
                {"status":"%s","handle_id":"%s","session_key":"%s","summary":%s}"""
                .formatted(r.status(), r.handleId(), r.sessionKey(), quote(r.summary()));
    }

    private String renderRunning(SubAgentResult r, boolean waited) {
        String note = waited
                ? "The subagent is still working after the wait timeout. It has NOT been cancelled — "
                        + "poll delegate_status with this handle_id, or carry on and check later."
                : "The subagent is running in the background. Poll delegate_status with this handle_id.";
        return """
                {"status":"RUNNING","handle_id":"%s","session_key":"%s","note":%s}"""
                .formatted(r.handleId(), r.sessionKey(), quote(note));
    }

    private static ToolProfile parseProfile(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ToolProfile.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // An unrecognised profile falls back to the configured default, which is
            // then narrowed to the parent's — never a widening.
            return null;
        }
    }

    private static int parseInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean parseBool(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString().trim());
    }

    static AgentRuntimeContext parentContext(ToolContext context) {
        if (context == null || context.contextData() == null) return null;
        Object value = context.contextData().get(AgentRuntime.AGENT_RUNTIME_CONTEXT_KEY);
        return value instanceof AgentRuntimeContext ctx ? ctx : null;
    }

    static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
