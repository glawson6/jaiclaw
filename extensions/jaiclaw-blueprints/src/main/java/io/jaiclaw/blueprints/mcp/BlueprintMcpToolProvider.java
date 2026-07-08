package io.jaiclaw.blueprints.mcp;

import io.jaiclaw.blueprints.BlueprintDefinition;
import io.jaiclaw.blueprints.BlueprintRegistry;
import io.jaiclaw.blueprints.BlueprintSlot;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;

import java.util.List;
import java.util.Map;

/**
 * Exposes the {@link BlueprintRegistry} over MCP so agents (and downstream
 * LLM clients like Claude Desktop) can discover blueprints available for
 * scheduling.
 *
 * <p>Tools:
 * <ul>
 *   <li>{@code blueprints_list} — enumerate every blueprint, optionally
 *       filtered by category</li>
 *   <li>{@code blueprints_get} — full detail of one blueprint by id,
 *       including slots + templates</li>
 * </ul>
 *
 * <p>Both tools return human-readable text, not JSON, to keep the LLM's
 * token cost low and the output directly quotable. A future
 * {@code blueprints_schedule} tool would actually create the cron job
 * from a filled blueprint — deferred until we wire the blueprint runtime
 * to {@code jaiclaw-cron}.
 */
public class BlueprintMcpToolProvider implements McpToolProvider {

    private static final String SERVER_NAME = "blueprints";
    private static final String TOOL_LIST = "blueprints_list";
    private static final String TOOL_GET = "blueprints_get";

    private final BlueprintRegistry registry;

    public BlueprintMcpToolProvider(BlueprintRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        this.registry = registry;
    }

    @Override
    public String getServerName() {
        return SERVER_NAME;
    }

    @Override
    public String getServerDescription() {
        return "Discover automation blueprints — parameterized scheduling templates that "
                + "can be turned into cron jobs. Use blueprints_list to browse, "
                + "blueprints_get to see the full template + slots for one blueprint.";
    }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition(TOOL_LIST,
                        "List automation blueprints. Optional 'category' filter narrows to one category.",
                        """
                        {
                          "type": "object",
                          "properties": {
                            "category": {
                              "type": "string",
                              "description": "Optional category filter (e.g. 'devops', 'research')."
                            }
                          }
                        }
                        """),
                new McpToolDefinition(TOOL_GET,
                        "Get the full definition of one blueprint by id — slots, schedule template, prompt template.",
                        """
                        {
                          "type": "object",
                          "properties": {
                            "id": {
                              "type": "string",
                              "description": "Blueprint id (e.g. 'daily-security-audit')."
                            }
                          },
                          "required": ["id"]
                        }
                        """));
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        return switch (toolName) {
            case TOOL_LIST -> list(args);
            case TOOL_GET -> get(args);
            default -> McpToolResult.error("Unknown tool: " + toolName);
        };
    }

    private McpToolResult list(Map<String, Object> args) {
        String category = args != null && args.get("category") instanceof String s && !s.isBlank() ? s : null;
        List<BlueprintDefinition> blueprints = category != null
                ? registry.byCategory(category)
                : registry.all();
        if (blueprints.isEmpty()) {
            return McpToolResult.success(category != null
                    ? "No blueprints in category '" + category + "'."
                    : "No blueprints registered.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Blueprints (").append(blueprints.size()).append("):\n\n");
        for (BlueprintDefinition d : blueprints) {
            sb.append("• [").append(d.category()).append("] ")
                    .append(d.id()).append(" — ").append(d.title()).append('\n');
            if (!d.scheduleHuman().isEmpty()) {
                sb.append("    schedule: ").append(d.scheduleHuman()).append('\n');
            }
            if (!d.description().isEmpty()) {
                sb.append("    ").append(d.description()).append('\n');
            }
            sb.append('\n');
        }
        return McpToolResult.success(sb.toString());
    }

    private McpToolResult get(Map<String, Object> args) {
        Object rawId = args != null ? args.get("id") : null;
        if (!(rawId instanceof String id) || id.isBlank()) {
            return McpToolResult.error("'id' is required and must be a non-blank string");
        }
        return registry.byId(id)
                .map(this::formatDetail)
                .map(McpToolResult::success)
                .orElseGet(() -> McpToolResult.error("Blueprint not found: " + id));
    }

    private String formatDetail(BlueprintDefinition d) {
        StringBuilder sb = new StringBuilder();
        sb.append(d.title()).append(" (").append(d.id()).append(")\n");
        sb.append("Category: ").append(d.category());
        if (!d.tags().isEmpty()) {
            sb.append("  Tags: ").append(String.join(", ", d.tags()));
        }
        sb.append('\n');
        if (!d.description().isEmpty()) {
            sb.append('\n').append(d.description()).append("\n");
        }
        sb.append("\nSchedule template: ").append(d.scheduleTemplate());
        sb.append("\nSchedule (human): ").append(d.scheduleHuman()).append("\n");
        sb.append("\nPrompt template:\n  ").append(d.promptTemplate()).append("\n");
        if (!d.slots().isEmpty()) {
            sb.append("\nSlots:\n");
            for (BlueprintSlot s : d.slots()) {
                sb.append("  - ").append(s.key())
                        .append(" (").append(s.type()).append(s.required() ? ", required" : ", optional")
                        .append(")");
                if (s.defaultValue() != null) sb.append("  default=\"").append(s.defaultValue()).append("\"");
                sb.append('\n');
                if (!s.description().isEmpty()) {
                    sb.append("      ").append(s.description()).append('\n');
                }
            }
        }
        return sb.toString();
    }
}
