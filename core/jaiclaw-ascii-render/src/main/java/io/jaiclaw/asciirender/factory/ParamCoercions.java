package io.jaiclaw.asciirender.factory;

import java.util.Map;

/**
 * Tiny set of {@code Object → primitive} coercion helpers used by
 * {@link ElementBuilders} and {@link AsciiSceneFactory}. Lifted from the
 * pre-extraction dispatch in the built-in {@code AsciiRenderTool}.
 *
 * <p>JSON deserialisation can land integers as {@link Number},
 * {@code String}, or — for client-tolerated quirks — anything with a
 * usable {@code toString()}. These helpers normalise the three cases
 * the dispatch actually sees in practice.
 */
final class ParamCoercions {

    private ParamCoercions() {}

    /**
     * Parse the value as an {@code Integer}, or {@code null} if missing,
     * unparseable, or {@code null}. Used for canvas-level optional ints
     * where "missing" and "garbage" should both fall back to the caller's
     * default rather than throwing.
     */
    static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Look up {@code key} as an {@code int}, returning {@code defaultValue}
     * when absent. Throws {@link IllegalArgumentException} on present-but-
     * malformed input so callers see a precise error.
     */
    static int intArg(Map<String, Object> p, String key, int defaultValue) {
        Object v = p.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "' must be an integer.");
        }
    }

    /** Look up {@code key} as a {@link String}, returning {@code defaultValue} when absent. */
    static String stringArg(Map<String, Object> p, String key, String defaultValue) {
        Object v = p.get(key);
        return v == null ? defaultValue : v.toString();
    }

    /**
     * Require a string value at {@code key}. The {@code label} is the
     * element kind used in the error message (e.g. "label", "text").
     */
    static String requireString(Map<String, Object> p, String key, String label) {
        Object v = p.get(key);
        if (v == null) {
            throw new IllegalArgumentException("'" + label + "' element requires '" + key + "'.");
        }
        return v.toString();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> castStringObjectMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }
}
