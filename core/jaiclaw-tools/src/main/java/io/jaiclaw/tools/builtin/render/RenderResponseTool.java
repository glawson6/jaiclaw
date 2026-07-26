package io.jaiclaw.tools.builtin.render;

import io.jaiclaw.asciirender.skill.RenderableTemplate;
import io.jaiclaw.asciirender.skill.RenderableTemplateRegistry;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Framework tool that dispatches LLM {@code render_response} calls to a
 * registered {@link RenderableTemplate} in the {@link RenderableTemplateRegistry}.
 *
 * <p>The tool's JSON schema is built dynamically at construction time from
 * the registry's contents:
 * <ul>
 *   <li>{@code template} is a required string; its {@code enum} values are
 *       {@link RenderableTemplateRegistry#names()}.</li>
 *   <li>Every name in
 *       {@link RenderableTemplateRegistry#unionParameterNames()} appears as
 *       an optional string property so the LLM can pass any param any
 *       registered template accepts.</li>
 * </ul>
 *
 * <p>On call, the tool looks up the named template and dispatches
 * {@link RenderableTemplate#render(Map)}. Empty template / unknown template
 * / registry-empty cases return {@link ToolResult.Error} with the available
 * names so the LLM's next attempt can pick a valid one.
 *
 * <p><strong>Logging is load-bearing.</strong> Three failure modes look
 * identical in a chat client: "model never called the tool", "model called
 * the tool then paraphrased the output", "model called the tool then
 * transliterated characters". The entry/exit INFO logs below are the only
 * way to disambiguate — the object-rendering skill's fidelity rules
 * (bundled in {@code jaiclaw-ascii-render/skills/object-rendering/SKILL.md})
 * depend on operators being able to see which failure mode fired.
 */
public class RenderResponseTool extends AbstractBuiltinTool {

    private static final Logger log = LoggerFactory.getLogger(RenderResponseTool.class);

    private final RenderableTemplateRegistry registry;

    public RenderResponseTool(RenderableTemplateRegistry registry) {
        super(new ToolDefinition(
                "render_response",
                "Render a domain object (event card, task kanban, ticket diff, order summary, …) "
                        + "as framed monospaced ASCII by dispatching to a named RenderableTemplate. "
                        + "Wrap the returned output in a triple-backtick code fence and paste it "
                        + "byte-for-byte as the reply — do NOT paraphrase, transliterate, forge, "
                        + "or strip borders. See the object-rendering bundled skill for the full "
                        + "fidelity rules.",
                ToolCatalog.SECTION_RENDERING,
                buildSchema(registry),
                Set.of(ToolProfile.FULL, ToolProfile.MESSAGING)
        ));
        this.registry = registry;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        if (registry.size() == 0) {
            String msg = "render_response — no RenderableTemplate beans registered. "
                    + "Adopter must register at least one @Bean implementing "
                    + "io.jaiclaw.asciirender.skill.RenderableTemplate.";
            log.warn("{}", msg);
            return new ToolResult.Error(msg);
        }

        String template = requireParam(parameters, "template");
        Optional<RenderableTemplate> hit = registry.find(template);

        if (hit.isEmpty()) {
            String msg = "render_response — unknown template '" + template + "'; known: "
                    + registry.names();
            log.warn("{}", msg);
            return new ToolResult.Error(msg);
        }

        log.info("render_response called — template={}, params={}", template, parameters.keySet());
        String output;
        try {
            output = hit.get().render(parameters);
        } catch (RuntimeException e) {
            // Templates should NOT throw per the SPI contract, but if one does,
            // surface it as a tool error rather than crashing the tool call —
            // matches the fidelity-rule contract: the LLM always gets something
            // parseable back.
            String msg = "render_response — template '" + template + "' threw "
                    + e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("{}", msg);
            return new ToolResult.Error(msg, e);
        }

        if (output == null) {
            output = "";
        }
        log.info("render_response — template={} produced {} chars", template, output.length());
        return new ToolResult.Success(output);
    }

    /**
     * Build the tool's JSON schema from the registry. Called once at
     * construction time. When the registry is empty, produces a schema
     * with {@code template.enum: []} — the tool loads but every call
     * errors out with the "no templates registered" message.
     */
    private static String buildSchema(RenderableTemplateRegistry registry) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"type\": \"object\",\n");
        sb.append("  \"properties\": {\n");
        sb.append("    \"template\": {\n");
        sb.append("      \"type\": \"string\",\n");
        sb.append("      \"description\": \"Which registered RenderableTemplate to dispatch to.\",\n");
        sb.append("      \"enum\": [");
        appendJsonStringArray(sb, registry.names());
        sb.append("]\n");
        sb.append("    }");

        for (String param : registry.unionParameterNames()) {
            sb.append(",\n");
            sb.append("    \"").append(jsonEscape(param)).append("\": {\n");
            sb.append("      \"type\": \"string\",\n");
            sb.append("      \"description\": \"Passed through to the selected template's render() call.\"\n");
            sb.append("    }");
        }

        sb.append("\n  },\n");
        sb.append("  \"required\": [\"template\"]\n");
        sb.append("}");
        return sb.toString();
    }

    private static void appendJsonStringArray(StringBuilder sb, Set<String> values) {
        Iterator<String> it = values.iterator();
        while (it.hasNext()) {
            String v = it.next();
            sb.append("\"").append(jsonEscape(v)).append("\"");
            if (it.hasNext()) sb.append(", ");
        }
    }

    private static String jsonEscape(String s) {
        // Names + param names are lowercase snake_case per the SPI contract,
        // so heavy escaping isn't needed. Guard the essentials anyway in case
        // an adopter uses names with special characters.
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
