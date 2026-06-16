package io.jaiclaw.asciirender.factory;

import java.util.List;

/**
 * Immutable, declarative description of an ASCII scene: canvas size,
 * an ordered list of elements, a trim flag, and an optional inner
 * padding margin. Produced by {@link AsciiSceneFactory#fromMap} or
 * {@link AsciiSceneFactory#fromJson}.
 *
 * <p>Records normalize a {@code null} elements list to an empty list
 * so consumers never have to null-check. The compact constructor
 * rejects non-positive canvas dimensions and clamps padding into a
 * sane range.
 *
 * @param width    canvas width in characters; must be positive
 * @param height   canvas height in characters; must be positive
 * @param elements scene elements in z-order (may be empty, never null)
 * @param trim     whether the renderer should trim trailing whitespace
 *                 from each output line
 * @param padding  inner-margin metadata carried with the scene. For
 *                 {@code ascii_box} this drives a real inner-margin
 *                 between the border and the content. For
 *                 {@code ascii_render} it is currently carried for
 *                 future use — the element positions in the spec are
 *                 honoured as-is. Clamped to {@code [0, 16]}; values
 *                 {@code < 0} are normalised to 0. Default {@code 0}
 *                 preserves pre-0.9.1 behaviour.
 */
public record SceneSpec(int width, int height, List<ElementSpec> elements, boolean trim, int padding) {

    /** Maximum supported padding — matches {@link AsciiBox#MAX_PADDING}. */
    public static final int MAX_PADDING = 16;

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
        if (padding < 0) padding = 0;
        if (padding > MAX_PADDING) padding = MAX_PADDING;
    }

    /** Convenience: build a scene with {@code trim=true} and no padding (the common case). */
    public SceneSpec(int width, int height, List<ElementSpec> elements) {
        this(width, height, elements, true, 0);
    }

    /** Backwards-compatible 4-arg constructor — defaults padding to 0. */
    public SceneSpec(int width, int height, List<ElementSpec> elements, boolean trim) {
        this(width, height, elements, trim, 0);
    }
}
