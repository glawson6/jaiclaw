package io.jaiclaw.tools.builtin;

import io.jaiclaw.asciirender.factory.AsciiBox;
import io.jaiclaw.asciirender.profile.AsciiRenderProfile;
import io.jaiclaw.asciirender.profile.AsciiRenderProfiles;
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
 * block of text in a Unicode box. Supports four border styles, an
 * optional title rendered on the top edge, optional inner padding,
 * and channel-aware rendering profiles.
 *
 * <p>All rendering lives in {@link AsciiBox} (in the library); this
 * tool is the parameter-coercion + Spring-AI adapter that surfaces it
 * to JaiClaw agents. Registered as a default built-in alongside
 * {@link AsciiRenderTool}.
 *
 * <p>Profile resolution (highest precedence first):
 * <ol>
 *   <li>Explicit {@code width} / {@code padding} parameters from the
 *       LLM win over the profile's defaults.</li>
 *   <li>Explicit {@code profile} parameter from the LLM selects a
 *       registered {@link AsciiRenderProfile}. This is the LLM-side
 *       override mechanism — agents pick a profile per-call regardless
 *       of the deployment default.</li>
 *   <li>Deployment default profile from
 *       {@code jaiclaw.ascii.default-profile}.</li>
 *   <li>Framework hardcoded fallback ({@code shell_80}).</li>
 * </ol>
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
                "profile": {
                  "type": "string",
                  "description": "Rendering profile name selecting channel-appropriate width and padding. Built-ins: shell_80, shell_120, telegram_desktop, telegram_mobile, slack_desktop, slack_mobile, discord_desktop, discord_mobile, email. Operators may register more via jaiclaw.ascii.profiles.*. Falls back to the deployment default (shell_80) when omitted or unknown."
                },
                "width": {
                  "type": "integer",
                  "description": "Maximum inner content width in characters. Overrides the profile's width when supplied."
                },
                "padding": {
                  "type": "integer",
                  "description": "Inner blank-row/column margin between the border and content. Overrides the profile's padding when supplied. Range 0..16."
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
                        + "operation. Supports four border styles, an optional title, optional inner "
                        + "padding, and channel-aware rendering profiles (telegram_mobile, "
                        + "slack_desktop, etc.) that pick appropriate width/padding for the target "
                        + "client.",
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

        AsciiRenderProfile profile = AsciiRenderProfiles.getOrDefault(
                stringOrNull(parameters.get("profile")));

        int width = resolveInt(parameters.get("width"), profile.width());
        int padding = resolveInt(parameters.get("padding"), profile.padding());
        AsciiBox.Style style = resolveBorder(parameters.get("border"));
        String title = stringOrNull(parameters.get("title"));

        return new ToolResult.Success(AsciiBox.render(content, width, padding, style, title));
    }

    /** Resolve an int param to its parsed value, falling back to {@code fallback} on any failure. */
    private static int resolveInt(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String stringOrNull(Object raw) {
        if (raw == null) return null;
        String s = raw.toString();
        return s.isBlank() ? null : s;
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
