package io.jaiclaw.core.tool.param;

import io.jaiclaw.core.api.Experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a record component with metadata that
 * {@link io.jaiclaw.core.tool.schema.SchemaBuilder} +
 * {@link io.jaiclaw.core.tool.param.SchemaInferrer} use to build the JSON
 * Schema for a tool's parameters, and that
 * {@link io.jaiclaw.core.tool.param.ParameterBinder} reads when binding
 * the LLM's {@code Map<String, Object>} payload to the record.
 *
 * <p>Pre-0.8.0 tool authors wrote two parallel things: a JSON Schema text
 * block describing the parameters, and a {@code requireParam(...)} call
 * for each field. Renaming a field meant editing both. With
 * {@code @ToolParameter} on a record component, the field name and
 * description are the single source of truth — schema and binding both
 * derive from the same annotation.
 *
 * <p>Example:
 *
 * <pre>{@code
 * public record WebFetchParams(
 *     @ToolParameter(description = "The URL to fetch content from")
 *     String url,
 *
 *     @ToolParameter(description = "Timeout in seconds (default 30)", required = false)
 *     Integer timeout,
 *
 *     @ToolParameter(description = "Extract clean readable text (default true)", required = false)
 *     Boolean extractReadable
 * ) {}
 * }</pre>
 *
 * <p>The annotation is retained at runtime ({@link RetentionPolicy#RUNTIME})
 * because the binding and inference happen on the live record component
 * descriptors at agent start-up.
 */
@Experimental
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface ToolParameter {

    /** Human-readable description shown to the LLM in the tool's JSON Schema. */
    String description();

    /**
     * Whether this parameter is required.
     *
     * <p>Required parameters land in the JSON Schema's {@code "required"} array.
     * Optional parameters are skipped from {@code "required"}; the LLM may
     * leave them out and the binder will pass {@code null} (for reference
     * types) or the boxed default for primitives.
     */
    boolean required() default true;
}
