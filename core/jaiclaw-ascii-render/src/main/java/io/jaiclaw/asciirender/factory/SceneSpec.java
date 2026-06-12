package io.jaiclaw.asciirender.factory;

import java.util.List;

/**
 * Immutable, declarative description of an ASCII scene: canvas size,
 * an ordered list of elements, and a trim flag. Produced by
 * {@link AsciiSceneFactory#fromMap} or {@link AsciiSceneFactory#fromJson}.
 *
 * <p>Records normalize a {@code null} elements list to an empty list
 * so consumers never have to null-check. The compact constructor
 * rejects non-positive canvas dimensions.
 */
public record SceneSpec(int width, int height, List<ElementSpec> elements, boolean trim) {

    public SceneSpec {
        if (width <= 0) {
            throw SceneSpecException.canvas("Missing or non-positive 'width'.");
        }
        if (height <= 0) {
            throw SceneSpecException.canvas("Missing or non-positive 'height'.");
        }
        if (elements == null) {
            elements = List.of();
        } else {
            elements = List.copyOf(elements);
        }
    }

    /** Convenience: build a scene with {@code trim=true} (the common case). */
    public SceneSpec(int width, int height, List<ElementSpec> elements) {
        this(width, height, elements, true);
    }
}
