package io.jaiclaw.tools.builtin;

import io.jaiclaw.asciirender.factory.AsciiBox;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Shortcut for the most common ASCII rendering operation: wrap a
 * block of text in a Unicode box. Supports four border styles and an
 * optional title rendered on the top edge.
 *
 * <p>All rendering lives in {@link AsciiBox} (in the library); this
 * tool is the parameter-coercion + Spring-AI adapter that surfaces it
 * to JaiClaw agents. Registered as a default built-in alongside
 * {@link AsciiRenderTool}.
 */
public class AsciiBoxTool extends AbstractBuiltinTool {

    private static final Logger log = LoggerFactory.getLogger(AsciiBoxTool.class);

    static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "content": {
                  "type": "string",
                  "description": "Text to put inside the box. Embedded newlines are honoured; long lines wrap at word boundaries."
                },
                "width": {
                  "type": "integer",
                  "description": "Maximum inner content width in characters (default 60). Box itself is two characters wider."
                },
                "border": {
                  "type": "string",
                  "description": "Border style: single | double | bold | rounded (default single)."
                },
                "title": {
                  "type": "string",
                  "description": "Optional title rendered on the top edge in [brackets]."
                }
              },
              "required": ["content"]
            }""";

    public AsciiBoxTool() {
        super(new ToolDefinition(
                "ascii_box",
                "Wrap text in a Unicode-bordered box. Quick path for the common ASCII rendering "
                        + "operation. Supports four border styles and an optional title.",
                ToolCatalog.SECTION_RENDERING,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL, ToolProfile.CODING, ToolProfile.MESSAGING)
        ));
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        Object rawContent = parameters.get("content");
        if (rawContent == null) {
            return new ToolResult.Error("Missing required parameter: content");
        }
        String content = rawContent.toString();
        int width = resolveWidth(parameters.get("width"));
        AsciiBox.Style style = resolveBorder(parameters.get("border"));
        String title = parameters.get("title") == null ? null : parameters.get("title").toString();

        return new ToolResult.Success(AsciiBox.render(content, width, style, title));
    }

    private static int resolveWidth(Object raw) {
        if (raw == null) {
            return AsciiBox.DEFAULT_WIDTH;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return AsciiBox.DEFAULT_WIDTH;
        }
    }

    private static AsciiBox.Style resolveBorder(Object raw) {
        if (raw == null) {
            return AsciiBox.Style.SINGLE;
        }
        AsciiBox.Style resolved = AsciiBox.Style.resolve(raw.toString());
        if (resolved == null) {
            log.warn("Unknown ascii_box border style '{}', falling back to 'single'", raw);
            return AsciiBox.Style.SINGLE;
        }
        return resolved;
    }
}
