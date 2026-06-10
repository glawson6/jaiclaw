package io.jaiclaw.core.tool.schema;

import io.jaiclaw.core.api.Experimental;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fluent builder for JSON Schema strings used as tool {@code inputSchema}.
 *
 * <p>Pre-0.8.0 tool authors hand-wrote schemas as text blocks (see
 * {@code WebFetchTool.INPUT_SCHEMA} pre-migration). This led to drift
 * between the schema and the {@code requireParam(...)} call sites: rename
 * a field in one, forget the other, and the LLM gets a parameter the
 * handler silently sees as {@code null}.
 *
 * <p>{@code SchemaBuilder} keeps both sides in sync. The same call that
 * adds a property to the schema can drive
 * {@link io.jaiclaw.core.tool.param.ParameterBinder} (when used with
 * {@link io.jaiclaw.core.tool.param.TypedToolCallback}) so renaming a
 * field is one edit, not two.
 *
 * <p>Example:
 *
 * <pre>{@code
 * static final String INPUT_SCHEMA = SchemaBuilder.object()
 *         .property("url",
 *                 new FieldSpec.StringField("The URL to fetch content from"))
 *         .property("timeout",
 *                 new FieldSpec.IntegerField("Timeout in seconds (default 30)", 1, 300))
 *         .property("extractReadable",
 *                 new FieldSpec.BooleanField(
 *                         "Extract clean readable text from HTML (default true)", true))
 *         .required("url")
 *         .toJsonString();
 * }</pre>
 *
 * <p>The builder is mutable and not thread-safe — instantiate once per
 * schema and call {@link #toJsonString()} (or {@link #toSchemaMap()})
 * exactly once.
 *
 * <p>Carved out as part of Phase 3 P3.2
 * (audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.2 step 2).
 */
@Experimental
public final class SchemaBuilder {

    private final Map<String, FieldSpec> properties = new LinkedHashMap<>();
    private final Set<String> required = new LinkedHashSet<>();

    private SchemaBuilder() {}

    /** Start building a top-level object schema (the only JSON Schema shape LLMs accept for tool params). */
    public static SchemaBuilder object() {
        return new SchemaBuilder();
    }

    /** Add a property to the schema. */
    public SchemaBuilder property(String name, FieldSpec spec) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("property name must not be blank");
        }
        if (spec == null) {
            throw new IllegalArgumentException("FieldSpec for property '" + name + "' must not be null");
        }
        properties.put(name, spec);
        return this;
    }

    /** Mark one or more properties as required. */
    public SchemaBuilder required(String... names) {
        if (names == null) return this;
        for (String name : names) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("required name must not be blank");
            }
            if (!properties.containsKey(name)) {
                throw new IllegalArgumentException(
                        "required('" + name + "') refers to a property that hasn't been added yet");
            }
            required.add(name);
        }
        return this;
    }

    /** Build the JSON Schema as a {@code Map<String, Object>}. */
    public Map<String, Object> toSchemaMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "object");

        Map<String, Object> propMap = new LinkedHashMap<>();
        properties.forEach((name, spec) -> propMap.put(name, spec.toSchemaMap()));
        out.put("properties", propMap);

        out.put("required", java.util.List.copyOf(required));
        return out;
    }

    /**
     * Build the JSON Schema as a JSON string.
     *
     * <p>jaiclaw-core has no Jackson dependency (the audit calls out core's
     * Spring-free, dependency-light invariant as a Phase 2 stability win),
     * so this method hand-rolls a compact JSON serializer over the
     * schema's known shape (strings, numbers, booleans, lists, maps).
     */
    public String toJsonString() {
        return toJson(toSchemaMap());
    }

    private static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof CharSequence cs) {
            appendJsonString(sb, cs.toString());
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<String, Object>) map).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                appendJsonString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                appendJson(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                appendJson(sb, item);
            }
            sb.append(']');
        } else {
            appendJsonString(sb, String.valueOf(value));
        }
    }

    private static void appendJsonString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
