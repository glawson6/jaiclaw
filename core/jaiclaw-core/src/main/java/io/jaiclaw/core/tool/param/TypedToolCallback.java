package io.jaiclaw.core.tool.param;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;

import java.util.Map;
import java.util.Set;

/**
 * Typed variant of {@link ToolCallback} that exchanges the
 * {@code Map<String, Object>} parameter bag for a strongly-typed
 * parameter record.
 *
 * <p>Authors implement four small methods to describe the tool and one
 * {@link #execute(Object, ToolContext) execute(P, ToolContext)} method
 * that receives the bound record. The base interface
 * ({@link ToolCallback}) is satisfied by default methods: the schema is
 * inferred from the record via {@link SchemaInferrer}, the LLM-supplied
 * parameter map is bound to the record via {@link ParameterBinder}, and
 * the resulting record is passed to {@link #execute(Object, ToolContext)}.
 *
 * <p>The legacy untyped {@link ToolCallback#execute(Map, ToolContext)}
 * path keeps working — {@code TypedToolCallback} is purely additive.
 * Authors can migrate one tool at a time, or leave hand-written-schema
 * tools as-is forever.
 *
 * <p>Example:
 *
 * <pre>{@code
 * public record WebFetchParams(
 *     @ToolParameter(description = "The URL to fetch content from")
 *     String url,
 *
 *     @ToolParameter(description = "Timeout in seconds (default 30)", required = false)
 *     Integer timeout
 * ) {}
 *
 * public class WebFetchTool implements TypedToolCallback<WebFetchParams> {
 *     @Override public Class<WebFetchParams> parameterType() { return WebFetchParams.class; }
 *     @Override public String toolName()        { return "web_fetch"; }
 *     @Override public String toolDescription() { return "Fetch content from a URL."; }
 *     @Override public String toolSection()     { return ToolCatalog.SECTION_WEB; }
 *
 *     @Override
 *     public ToolResult execute(WebFetchParams params, ToolContext context) {
 *         int timeout = params.timeout() != null ? params.timeout() : 30;
 *         // ... type-safe; no requireParam(...) casting
 *     }
 * }
 * }</pre>
 *
 * <p>Phase 3 P3.2; audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.2 step 3.
 *
 * @param <P> the parameter record type carrying {@code @ToolParameter}-
 *            annotated components
 */
@Experimental
public interface TypedToolCallback<P> extends ToolCallback {

    // ── Author hooks ─────────────────────────────────────────────

    /** The parameter record class. Must be a record. */
    Class<P> parameterType();

    /** The tool name the LLM sees (e.g. {@code "web_fetch"}). */
    String toolName();

    /** Tool description the LLM sees. */
    String toolDescription();

    /** Run the tool with the typed parameter. */
    ToolResult execute(P params, ToolContext context);

    /** Tool catalog section (e.g. {@code ToolCatalog.SECTION_WEB}); default is {@code "custom"}. */
    default String toolSection() { return "custom"; }

    /** Profiles this tool participates in. Default: {@link ToolProfile#FULL}. */
    default Set<ToolProfile> toolProfiles() { return Set.of(ToolProfile.FULL); }

    // ── ToolCallback bridge ──────────────────────────────────────

    @Override
    default ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(toolName())
                .description(toolDescription())
                .section(toolSection())
                .inputSchema(SchemaInferrer.inferSchemaString(parameterType()))
                .profiles(toolProfiles())
                .build();
    }

    @Override
    default ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        P typed;
        try {
            typed = ParameterBinder.bind(parameters, parameterType());
        } catch (IllegalArgumentException e) {
            return new ToolResult.Error(e.getMessage());
        }
        return execute(typed, context);
    }
}
