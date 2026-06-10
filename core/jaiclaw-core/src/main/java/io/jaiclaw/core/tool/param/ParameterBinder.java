package io.jaiclaw.core.tool.param;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Map;

/**
 * Binds a {@code Map<String, Object>} (the shape the LLM hands to a tool) to a
 * typed parameter record.
 *
 * <p>The mapping respects {@link ToolParameter}'s {@code required} flag:
 * required components missing from the map cause an
 * {@link IllegalArgumentException}; optional components default to
 * {@code null} for reference types and Java's boxed default for primitives.
 *
 * <p>Numeric components accept any {@code Number} from the map — the JSON
 * decoder Spring AI uses (Jackson) can return {@code Integer}, {@code Long},
 * or {@code Double} depending on context; this binder normalises them to
 * the declared component type.
 *
 * <p>Carved out as part of Phase 3 P3.2
 * (audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.2 step 3).
 */
public final class ParameterBinder {

    private ParameterBinder() {}

    /**
     * Bind the given parameter map to an instance of {@code recordType}.
     *
     * @param parameters the raw map handed in by the LLM
     * @param recordType the target parameter record (must be a record class)
     * @param <P>        the parameter record type
     * @return a populated instance
     * @throws IllegalArgumentException if a required field is missing or a
     *                                  value cannot be coerced to the
     *                                  declared component type
     */
    public static <P> P bind(Map<String, Object> parameters, Class<P> recordType) {
        if (recordType == null || !recordType.isRecord()) {
            throw new IllegalArgumentException(
                    "Parameter type must be a record. Got: "
                            + (recordType == null ? "null" : recordType.getName()));
        }
        if (parameters == null) {
            parameters = Map.of();
        }

        RecordComponent[] components = recordType.getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] paramTypes = new Class<?>[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent rc = components[i];
            paramTypes[i] = rc.getType();

            ToolParameter ann = rc.getAnnotation(ToolParameter.class);
            boolean required = ann == null || ann.required();
            Object raw = parameters.get(rc.getName());

            if (raw == null) {
                if (required) {
                    throw new IllegalArgumentException(
                            "missing required parameter: " + rc.getName());
                }
                args[i] = defaultFor(rc.getType());
                continue;
            }

            args[i] = coerce(raw, rc.getType(), rc.getName());
        }

        try {
            Constructor<P> ctor = recordType.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "Could not locate canonical constructor for record " + recordType.getName(), e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            Throwable root = e instanceof InvocationTargetException ite ? ite.getCause() : e;
            throw new IllegalArgumentException(
                    "Failed to instantiate parameter record " + recordType.getSimpleName()
                            + ": " + root.getMessage(), root);
        }
    }

    /** Default value for a Java type: {@code 0}/{@code false} for primitives, {@code null} otherwise. */
    private static Object defaultFor(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == int.class)     return 0;
        if (type == long.class)    return 0L;
        if (type == short.class)   return (short) 0;
        if (type == byte.class)    return (byte) 0;
        if (type == double.class)  return 0.0;
        if (type == float.class)   return 0.0f;
        if (type == boolean.class) return false;
        if (type == char.class)    return '\0';
        return null;
    }

    /** Coerce a raw map value to the declared component type. */
    private static Object coerce(Object raw, Class<?> target, String fieldName) {
        // Exact match shortcut
        if (target.isInstance(raw)) {
            return raw;
        }
        // Primitive widening / boxing
        if (target == String.class) {
            return raw.toString();
        }
        if (raw instanceof Number n) {
            if (target == Integer.class || target == int.class)       return n.intValue();
            if (target == Long.class    || target == long.class)      return n.longValue();
            if (target == Short.class   || target == short.class)     return n.shortValue();
            if (target == Byte.class    || target == byte.class)      return n.byteValue();
            if (target == Double.class  || target == double.class)    return n.doubleValue();
            if (target == Float.class   || target == float.class)     return n.floatValue();
        }
        if (raw instanceof Boolean b && (target == Boolean.class || target == boolean.class)) {
            return b;
        }
        // Allow string → number / boolean for LLMs that return everything as text
        if (raw instanceof String s) {
            try {
                if (target == Integer.class || target == int.class)   return Integer.parseInt(s);
                if (target == Long.class    || target == long.class)  return Long.parseLong(s);
                if (target == Double.class  || target == double.class) return Double.parseDouble(s);
                if (target == Float.class   || target == float.class)  return Float.parseFloat(s);
                if (target == Boolean.class || target == boolean.class) return Boolean.parseBoolean(s);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "parameter '" + fieldName + "' could not be parsed as " + target.getSimpleName()
                                + ": '" + s + "'");
            }
        }
        throw new IllegalArgumentException(
                "parameter '" + fieldName + "' expected " + target.getSimpleName()
                        + " but got " + raw.getClass().getSimpleName());
    }
}
