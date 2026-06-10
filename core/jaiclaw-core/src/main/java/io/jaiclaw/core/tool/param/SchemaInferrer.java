package io.jaiclaw.core.tool.param;

import io.jaiclaw.core.tool.schema.FieldSpec;
import io.jaiclaw.core.tool.schema.SchemaBuilder;

import java.lang.reflect.RecordComponent;
import java.util.List;

/**
 * Walks a parameter-record's components and produces a JSON Schema string
 * via {@link SchemaBuilder} + {@link FieldSpec}.
 *
 * <p>Each component must carry a {@link ToolParameter} annotation; the
 * component's declared Java type drives the {@link FieldSpec} subtype
 * chosen (e.g. {@code String} → {@link FieldSpec.StringField},
 * {@code Integer}/{@code int} → {@link FieldSpec.IntegerField}, etc.).
 *
 * <p>Carved out as part of Phase 3 P3.2
 * (audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.2 step 3).
 */
public final class SchemaInferrer {

    private SchemaInferrer() {}

    /**
     * Infer the JSON Schema string for the given parameter record.
     *
     * @param paramRecord the {@code Record} subclass whose components carry
     *                    {@code @ToolParameter} annotations
     * @return JSON Schema string suitable for {@code ToolDefinition.inputSchema(...)}
     * @throws IllegalArgumentException if {@code paramRecord} is not a record,
     *                                  or any component lacks {@code @ToolParameter}
     */
    public static String inferSchemaString(Class<?> paramRecord) {
        return inferSchema(paramRecord).toJsonString();
    }

    /** Same as {@link #inferSchemaString(Class)} but returns the {@link SchemaBuilder} so callers can append further fields. */
    public static SchemaBuilder inferSchema(Class<?> paramRecord) {
        if (paramRecord == null || !paramRecord.isRecord()) {
            throw new IllegalArgumentException(
                    "Parameter type must be a record. Got: "
                            + (paramRecord == null ? "null" : paramRecord.getName()));
        }

        SchemaBuilder schema = SchemaBuilder.object();
        List<String> requiredNames = new java.util.ArrayList<>();

        for (RecordComponent rc : paramRecord.getRecordComponents()) {
            ToolParameter ann = rc.getAnnotation(ToolParameter.class);
            if (ann == null) {
                throw new IllegalArgumentException(
                        "Record component '" + rc.getName()
                                + "' on " + paramRecord.getSimpleName()
                                + " is missing @ToolParameter — annotate every component or "
                                + "use the legacy ToolCallback SPI for hand-written schemas.");
            }
            schema.property(rc.getName(), toFieldSpec(rc, ann));
            if (ann.required()) {
                requiredNames.add(rc.getName());
            }
        }

        if (!requiredNames.isEmpty()) {
            schema.required(requiredNames.toArray(String[]::new));
        }
        return schema;
    }

    /** Map a record component's Java type + {@link ToolParameter} to a {@link FieldSpec}. */
    private static FieldSpec toFieldSpec(RecordComponent rc, ToolParameter ann) {
        Class<?> type = rc.getType();
        String description = ann.description();

        if (type == String.class) {
            return new FieldSpec.StringField(description);
        }
        if (type == Integer.class || type == int.class
                || type == Long.class || type == long.class
                || type == Short.class || type == short.class) {
            return new FieldSpec.IntegerField(description);
        }
        if (type == Double.class || type == double.class
                || type == Float.class || type == float.class) {
            return new FieldSpec.NumberField(description);
        }
        if (type == Boolean.class || type == boolean.class) {
            return new FieldSpec.BooleanField(description);
        }
        if (type == java.util.List.class || type.isArray()) {
            // Lists of String for now — typed list elements are a Phase 4 item.
            return new FieldSpec.ArrayField(description, new FieldSpec.StringField("item"));
        }
        throw new IllegalArgumentException(
                "Unsupported @ToolParameter type for component '" + rc.getName()
                        + "': " + type.getName() + ". Supported: String, Integer/Long/Short, "
                        + "Double/Float, Boolean, List, arrays. Nested records and complex "
                        + "object types are out of scope for 0.8.0 — use the legacy "
                        + "ToolCallback SPI with a hand-written schema for those cases.");
    }
}
