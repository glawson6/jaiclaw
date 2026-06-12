package io.jaiclaw.tools.builtin;

import io.jaiclaw.asciirender.factory.AsciiSceneFactory;
import io.jaiclaw.asciirender.factory.SceneSpec;
import io.jaiclaw.asciirender.factory.SceneSpecException;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

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
 * <p>This tool is registered as a default built-in in
 * {@link BuiltinTools#all(io.jaiclaw.tools.exec.ExecPolicyConfig, boolean)}
 * so every JaiClaw agent can render diagrams without extra wiring.
 */
public class AsciiRenderTool extends AbstractBuiltinTool {

    static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "width": {
                  "type": "integer",
                  "description": "Canvas width in characters (e.g. 80)"
                },
                "height": {
                  "type": "integer",
                  "description": "Canvas height in characters (e.g. 24)"
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
              "required": ["width", "height", "elements"]
            }""";

    public AsciiRenderTool() {
        super(new ToolDefinition(
                "ascii_render",
                "Render a declarative scene (rectangles, lines, labels, text blocks, dots, "
                        + "circles, ellipses, tables, scatter plots) to ASCII art. Use for "
                        + "diagrams, boxes, charts that should be embedded in chat responses.",
                ToolCatalog.SECTION_RENDERING,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL, ToolProfile.CODING, ToolProfile.MESSAGING)
        ));
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        try {
            SceneSpec scene = AsciiSceneFactory.fromMap(parameters);
            return new ToolResult.Success(AsciiSceneFactory.render(scene));
        } catch (SceneSpecException e) {
            return new ToolResult.Error(formatError(e));
        }
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
