package io.jaiclaw.asciirender.factory;

import java.util.Map;

/**
 * Declarative description of a single element in an ASCII scene.
 *
 * <p>{@code type} selects the constructor in
 * {@link io.jaiclaw.asciirender.element} (e.g. {@code "rectangle"},
 * {@code "label"}, {@code "plot"}). {@code params} carries the keyed
 * values the chosen element accepts; missing keys fall back to the
 * library's "auto" sentinel ({@link Integer#MIN_VALUE}) where
 * supported.
 *
 * <p>A {@code null} {@code params} map is normalised to {@link Map#of()}
 * by the compact constructor so element builders never need to null-check.
 */
public record ElementSpec(String type, Map<String, Object> params) {

    public ElementSpec {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("ElementSpec.type must not be blank");
        }
        if (params == null) {
            params = Map.of();
        }
    }

    /** Convenience for elements that take no params (e.g. canvas-filling rectangle). */
    public static ElementSpec of(String type) {
        return new ElementSpec(type, Map.of());
    }
}
