package io.jaiclaw.tools.builtin;

import io.jaiclaw.asciirender.factory.AsciiSceneFactory;
import io.jaiclaw.asciirender.factory.SceneSpec;
import io.jaiclaw.asciirender.factory.SceneSpecException;
import io.jaiclaw.asciirender.profile.AsciiRenderProfile;
import io.jaiclaw.asciirender.profile.AsciiRenderProfiles;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Render an LLM-supplied declarative scene to ASCII text.
 *
 * <p>The scene is a {@code width × height} canvas plus a flat list of
 * elements drawn in z-order. All dispatch and rendering lives in the
 * library facade {@link AsciiSceneFactory}; this tool is a thin Spring/
 * MCP-friendly adapter that converts the parameter map into a
 * {@link SceneSpec}, calls the factory, and wraps the result.
 *
 * <p>Channel-aware rendering profiles supply a default canvas width
 * when the LLM omits {@code width}, and carry a {@code padding} value
 * onto the resulting {@link SceneSpec}. Explicit {@code width} /
 * {@code padding} parameters from the LLM always win over the
 * profile's defaults.
 *
 * <p>This tool is registered as a default built-in in
 * {@link BuiltinTools#all(io.jaiclaw.tools.exec.ExecPolicyConfig, boolean)}
 * so every JaiClaw agent can render diagrams without extra wiring.
 */
public class AsciiRenderTool extends AbstractBuiltinTool {

    static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "profile": {
                  "type": "string",
                  "description": "Rendering profile name selecting channel-appropriate width and padding defaults. Built-ins: shell_80, shell_120, telegram_desktop, telegram_mobile, slack_desktop, slack_mobile, discord_desktop, discord_mobile, email. When supplied, the profile's width is used as the canvas width if the explicit 'width' parameter is omitted. Falls back to the deployment default (shell_80) when omitted or unknown."
                },
                "width": {
                  "type": "integer",
                  "description": "Canvas width in characters (e.g. 80). Overrides the profile's width when supplied."
                },
                "height": {
                  "type": "integer",
                  "description": "Canvas height in characters (e.g. 24)."
                },
                "padding": {
                  "type": "integer",
                  "description": "Uniform inner margin metadata carried on the scene (range 0..16). Overrides the profile's padding when supplied."
                },
                "elements": {
                  "type": "array",
                  "description": "Scene elements in z-order. Each entry: {\\"type\\": string, \\"params\\": object}. Types: rectangle | line | label | text | dot | circle | ellipse | table | plot.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "type": {"type": "string"},
                      "params": {"type": "object"}
                    },
                    "required": ["type"]
                  }
                },
                "trim": {
                  "type": "boolean",
                  "description": "Trim trailing whitespace from each rendered line (default true)"
                }
              },
              "required": ["height", "elements"]
            }""";

    public AsciiRenderTool() {
        super(new ToolDefinition(
                "ascii_render",
                "Render a declarative scene (rectangles, lines, labels, text blocks, dots, "
                        + "circles, ellipses, tables, scatter plots) to ASCII art. Use for "
                        + "diagrams, boxes, charts that should be embedded in chat responses. "
                        + "Supports channel-aware rendering profiles (telegram_mobile, "
                        + "slack_desktop, etc.) that supply width / padding defaults when omitted.",
                ToolCatalog.SECTION_RENDERING,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL, ToolProfile.CODING, ToolProfile.MESSAGING)
        ));
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        try {
            Map<String, Object> resolved = applyProfileDefaults(parameters);
            SceneSpec scene = AsciiSceneFactory.fromMap(resolved);
            return new ToolResult.Success(AsciiSceneFactory.render(scene));
        } catch (SceneSpecException e) {
            return new ToolResult.Error(formatError(e));
        }
    }

    /**
     * Mix profile defaults into the parameter map so the existing
     * {@link AsciiSceneFactory#fromMap} parser picks them up without
     * needing a new code path. Explicit caller-supplied values win.
     */
    private static Map<String, Object> applyProfileDefaults(Map<String, Object> parameters) {
        Object profileName = parameters.get("profile");
        AsciiRenderProfile profile = AsciiRenderProfiles.getOrDefault(
                profileName == null ? null : profileName.toString());

        Map<String, Object> resolved = new HashMap<>(parameters);
        if (!resolved.containsKey("width") || resolved.get("width") == null) {
            resolved.put("width", profile.width());
        }
        if (!resolved.containsKey("padding") || resolved.get("padding") == null) {
            resolved.put("padding", profile.padding());
        }
        return resolved;
    }

    private static String formatError(SceneSpecException e) {
        if (e.elementIndex() < 0) {
            return e.getMessage();
        }
        String type = e.elementType();
        if (type == null) {
            return "Element[" + e.elementIndex() + "] " + e.getMessage();
        }
        if (e.getMessage().startsWith("Unknown element type")) {
            return "Element[" + e.elementIndex() + "]: " + lowercaseFirst(e.getMessage());
        }
        return "Element[" + e.elementIndex() + "] (" + type + "): " + e.getMessage();
    }

    private static String lowercaseFirst(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
