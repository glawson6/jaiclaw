package io.jaiclaw.tools.builtin;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.core.tool.param.ParameterBinder;
import io.jaiclaw.core.tool.param.SchemaInferrer;
import io.jaiclaw.core.tool.param.TypedToolCallback;

import java.util.Map;
import java.util.Set;

/**
 * Typed counterpart of {@link AbstractBuiltinTool}.
 *
 * <p>Bridges {@link TypedToolCallback} into the existing built-in-tool
 * convention: the {@link ToolDefinition} is constructed once in the
 * constructor (so callers can introspect tool metadata without paying
 * the reflection cost on every call) and execution wraps any thrown
 * exception in a {@link ToolResult.Error}.
 *
 * <p>Subclasses implement {@link #doExecute(Object, ToolContext)} with a
 * typed parameter record. Phase 3 P3.2; audit
 * {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.2 step 3.
 *
 * @param <P> the parameter record type carrying
 *            {@code @io.jaiclaw.core.tool.param.ToolParameter}
 *            annotations
 */
public abstract class AbstractTypedBuiltinTool<P> implements TypedToolCallback<P> {

    private final Class<P> parameterType;
    private final ToolDefinition definition;

    protected AbstractTypedBuiltinTool(Class<P> parameterType, String name, String description,
                                        String section, Set<ToolProfile> profiles) {
        this.parameterType = parameterType;
        this.definition = ToolDefinition.builder()
                .name(name)
                .description(description)
                .section(section)
                .inputSchema(SchemaInferrer.inferSchemaString(parameterType))
                .profiles(profiles != null ? profiles : Set.of(ToolProfile.FULL))
                .build();
    }

    // ── TypedToolCallback wiring ─────────────────────────────────

    @Override public final Class<P> parameterType() { return parameterType; }
    @Override public final String toolName()        { return definition.name(); }
    @Override public final String toolDescription() { return definition.description(); }
    @Override public final String toolSection()     { return definition.section(); }
    @Override public final Set<ToolProfile> toolProfiles() { return definition.profiles(); }

    @Override
    public final ToolDefinition definition() {
        return definition;
    }

    @Override
    public final ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        P typed;
        try {
            typed = ParameterBinder.bind(parameters, parameterType);
        } catch (IllegalArgumentException e) {
            return new ToolResult.Error(e.getMessage());
        }
        try {
            return doExecute(typed, context);
        } catch (Exception e) {
            return new ToolResult.Error(e.getMessage(), e);
        }
    }

    /** Required for the no-typed-bind path inherited from {@link TypedToolCallback}. */
    @Override
    public final ToolResult execute(P params, ToolContext context) {
        try {
            return doExecute(params, context);
        } catch (Exception e) {
            return new ToolResult.Error(e.getMessage(), e);
        }
    }

    /** Subclass implements the actual work. */
    protected abstract ToolResult doExecute(P params, ToolContext context) throws Exception;
}
