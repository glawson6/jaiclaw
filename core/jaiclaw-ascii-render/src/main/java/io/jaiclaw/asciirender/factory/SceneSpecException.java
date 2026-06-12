package io.jaiclaw.asciirender.factory;

/**
 * Structured failure from {@link AsciiSceneFactory}. Subclass of
 * {@link IllegalArgumentException} so existing catch sites (notably
 * the built-in {@code AsciiRenderTool}) continue to handle it
 * uniformly with element-constructor validation errors.
 *
 * <p>Carries the position of the failing element (or {@code -1} for
 * canvas-level errors) and its declared type, so callers can format
 * "Element[i] (type): message"-style diagnostics without re-deriving
 * the index.
 */
public final class SceneSpecException extends IllegalArgumentException {

    private final int elementIndex;
    private final String elementType;

    private SceneSpecException(int elementIndex, String elementType, String message, Throwable cause) {
        super(message, cause);
        this.elementIndex = elementIndex;
        this.elementType = elementType;
    }

    /** Canvas-level error (missing width/height, malformed JSON, etc.). */
    public static SceneSpecException canvas(String message) {
        return new SceneSpecException(-1, null, message, null);
    }

    /** Canvas-level error wrapping an underlying cause. */
    public static SceneSpecException canvas(String message, Throwable cause) {
        return new SceneSpecException(-1, null, message, cause);
    }

    /** Per-element error keyed by 0-based index and element type. */
    public static SceneSpecException element(int index, String type, String message) {
        return new SceneSpecException(index, type, message, null);
    }

    public static SceneSpecException element(int index, String type, String message, Throwable cause) {
        return new SceneSpecException(index, type, message, cause);
    }

    /** 0-based position in the elements array, or {@code -1} for canvas-level errors. */
    public int elementIndex() {
        return elementIndex;
    }

    /** The failing element's declared {@code type}, or {@code null} for canvas-level errors. */
    public String elementType() {
        return elementType;
    }
}
