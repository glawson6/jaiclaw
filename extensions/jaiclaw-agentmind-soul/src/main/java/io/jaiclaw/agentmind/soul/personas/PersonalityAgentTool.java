package io.jaiclaw.agentmind.soul.personas;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Set;

/**
 * Agent tool {@code personality}: switches the active persona overlay for
 * the current session.
 *
 * <p>Three actions:
 * <ul>
 *   <li>{@code set} — activate a persona by name; the next system-prompt
 *       build picks up the overlay</li>
 *   <li>{@code clear} — drop any active persona for the session</li>
 *   <li>{@code list} — return the available persona names</li>
 * </ul>
 *
 * <p>Setting a persona that isn't loaded returns a typed error so the
 * agent can self-correct rather than silently ignoring.
 *
 * <p>Plan §8 task 4.3.
 */
public class PersonalityAgentTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
              "action":{"type":"string","enum":["set","clear","list"],
                        "description":"What to do."},
              "name":{"type":"string","description":"Persona name (required for action=set)."}
            },"required":["action"]}""";

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "personality",
            "Switch the active persona overlay for the current session. "
                    + "Actions: set (activate by name), clear (drop), list (see what's available).",
            "Soul",
            INPUT_SCHEMA);

    private static final Set<String> ALLOWED_ACTIONS = Set.of("set", "clear", "list");

    private final PersonaOverlayManager manager;

    public PersonalityAgentTool(PersonaOverlayManager manager) {
        super(DEFINITION);
        this.manager = manager;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String action = requireParam(parameters, "action").toLowerCase();
        if (!ALLOWED_ACTIONS.contains(action)) {
            return new ToolResult.Error("Unknown action: " + action
                    + ". Allowed: set, clear, list.");
        }
        String sessionKey = context.sessionKey();
        if (sessionKey == null || sessionKey.isBlank()) {
            return new ToolResult.Error(
                    "No sessionKey on tool context — cannot scope persona to a session.");
        }

        return switch (action) {
            case "set" -> handleSet(parameters, sessionKey);
            case "clear" -> handleClear(sessionKey);
            case "list" -> handleList();
            default -> new ToolResult.Error("Unreachable: " + action);
        };
    }

    private ToolResult handleSet(Map<String, Object> parameters, String sessionKey) {
        String name = optionalParam(parameters, "name", null);
        if (name == null || name.isBlank()) {
            return new ToolResult.Error("action=set requires the 'name' parameter.");
        }
        if (!manager.activate(sessionKey, name)) {
            return new ToolResult.Error(
                    "Unknown persona: '" + name + "'. Available: " + manager.available());
        }
        return new ToolResult.Success("Persona '" + name + "' active for this session.");
    }

    private ToolResult handleClear(String sessionKey) {
        manager.clear(sessionKey);
        return new ToolResult.Success("Persona cleared for this session.");
    }

    private ToolResult handleList() {
        return new ToolResult.Success("Available personas: " + manager.available());
    }
}
