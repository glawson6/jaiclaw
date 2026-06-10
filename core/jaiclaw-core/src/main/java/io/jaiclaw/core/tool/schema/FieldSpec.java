package io.jaiclaw.core.tool.schema;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sealed description of one JSON Schema property used by
 * {@link SchemaBuilder} to build a tool's input schema.
 *
 * <p>Pre-0.8.0 tool authors hand-wrote the schema as a multi-line text
 * block (see {@code WebFetchTool.INPUT_SCHEMA} pre-migration), which
 * could drift silently from the {@code requireParam(...)} extraction
 * call sites. The sealed {@link FieldSpec} hierarchy keeps the two
 * sides in sync — the same builder call describes both shape and
 * extraction.
 *
 * <p>Each subtype maps to one JSON Schema type:
 *
 * <ul>
 *   <li>{@link StringField} → {@code "type": "string"} (+ optional
 *       {@code enum} / {@code maxLength})</li>
 *   <li>{@link IntegerField} → {@code "type": "integer"} (+ optional
 *       {@code minimum} / {@code maximum})</li>
 *   <li>{@link NumberField} → {@code "type": "number"} (+ optional
 *       {@code minimum} / {@code maximum})</li>
 *   <li>{@link BooleanField} → {@code "type": "boolean"} (+ optional
 *       {@code default})</li>
 *   <li>{@link ArrayField} → {@code "type": "array"} with an
 *       {@code items} sub-spec</li>
 *   <li>{@link ObjectField} → {@code "type": "object"} with nested
 *       properties</li>
 * </ul>
 */
public sealed interface FieldSpec
        permits FieldSpec.StringField, FieldSpec.IntegerField,
                FieldSpec.NumberField, FieldSpec.BooleanField,
                FieldSpec.ArrayField, FieldSpec.ObjectField {

    /** Human-readable description shown to the LLM. */
    String description();

    /** JSON Schema {@code "type"} keyword for this field. */
    String type();

    /**
     * Render this field as a {@code Map<String, Object>} that can be
     * serialized to JSON by the caller.
     */
    Map<String, Object> toSchemaMap();

    // ─── String ──────────────────────────────────────────────────

    record StringField(
            String description,
            List<String> enumValues,
            Integer maxLength
    ) implements FieldSpec {

        public StringField(String description) {
            this(description, null, null);
        }

        @Override public String type() { return "string"; }

        @Override public Map<String, Object> toSchemaMap() {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("type", "string");
            out.put("description", description);
            if (enumValues != null && !enumValues.isEmpty()) {
                out.put("enum", List.copyOf(enumValues));
            }
            if (maxLength != null) {
                out.put("maxLength", maxLength);
            }
            return out;
        }
    }

    // ─── Integer ─────────────────────────────────────────────────

    record IntegerField(
            String description,
            Integer minimum,
            Integer maximum
    ) implements FieldSpec {

        public IntegerField(String description) {
            this(description, null, null);
        }

        @Override public String type() { return "integer"; }

        @Override public Map<String, Object> toSchemaMap() {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("type", "integer");
            out.put("description", description);
            if (minimum != null) out.put("minimum", minimum);
            if (maximum != null) out.put("maximum", maximum);
            return out;
        }
    }

    // ─── Number (double) ─────────────────────────────────────────

    record NumberField(
            String description,
            Double minimum,
            Double maximum
    ) implements FieldSpec {

        public NumberField(String description) {
            this(description, null, null);
        }

        @Override public String type() { return "number"; }

        @Override public Map<String, Object> toSchemaMap() {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("type", "number");
            out.put("description", description);
            if (minimum != null) out.put("minimum", minimum);
            if (maximum != null) out.put("maximum", maximum);
            return out;
        }
    }

    // ─── Boolean ─────────────────────────────────────────────────

    record BooleanField(
            String description,
            Boolean defaultValue
    ) implements FieldSpec {

        public BooleanField(String description) {
            this(description, null);
        }

        @Override public String type() { return "boolean"; }

        @Override public Map<String, Object> toSchemaMap() {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("type", "boolean");
            out.put("description", description);
            if (defaultValue != null) out.put("default", defaultValue);
            return out;
        }
    }

    // ─── Array ───────────────────────────────────────────────────

    record ArrayField(
            String description,
            FieldSpec items
    ) implements FieldSpec {

        @Override public String type() { return "array"; }

        @Override public Map<String, Object> toSchemaMap() {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("type", "array");
            out.put("description", description);
            if (items != null) {
                out.put("items", items.toSchemaMap());
            }
            return out;
        }
    }

    // ─── Object (nested) ─────────────────────────────────────────

    record ObjectField(
            String description,
            Map<String, FieldSpec> properties,
            Set<String> required
    ) implements FieldSpec {

        @Override public String type() { return "object"; }

        @Override public Map<String, Object> toSchemaMap() {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("type", "object");
            out.put("description", description);

            Map<String, Object> props = new java.util.LinkedHashMap<>();
            if (properties != null) {
                properties.forEach((k, v) -> props.put(k, v.toSchemaMap()));
            }
            out.put("properties", props);

            if (required != null) {
                out.put("required", List.copyOf(required));
            } else {
                out.put("required", List.of());
            }
            return out;
        }
    }
}
