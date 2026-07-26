package io.jaiclaw.asciirender.factory;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IContextBuilder;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.core.Region;
import io.jaiclaw.asciirender.core.Render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.jaiclaw.asciirender.factory.ParamCoercions.asInteger;
import static io.jaiclaw.asciirender.factory.ParamCoercions.castStringObjectMap;

/**
 * Turn a declarative scene description into ASCII text.
 *
 * <p>Three input forms are supported:
 *
 * <ul>
 *   <li>{@link #fromMap(Map)} — accepts a {@code Map<String, Object>}
 *       (typical for Spring {@code MessageConverter} output, the
 *       built-in {@code AsciiRenderTool}, MCP arguments).</li>
 *   <li>{@link #fromJson(String)} — accepts a JSON document directly,
 *       parsed via a private {@link ObjectMapper}.</li>
 *   <li>Hand-construct a {@link SceneSpec} via its records.</li>
 * </ul>
 *
 * <p>{@link #render(SceneSpec)} composes the library's
 * {@link Render#builder()} pipeline. {@link #toContext(SceneSpec)}
 * exposes the intermediate {@link IContext} for callers that want to
 * add more elements before rendering.
 *
 * <p>JSON schema for the input:
 * <pre>{@code
 * {
 *   "width":  <int>,
 *   "height": <int>,
 *   "trim":   <bool>   // optional, default true
 *   "elements": [
 *     {"type": <string>, "params": { ... element-specific keys ... }},
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * <p>Supported {@code type} values: {@code rectangle}, {@code line},
 * {@code label}, {@code text}, {@code dot}, {@code circle},
 * {@code ellipse}, {@code table}, {@code plot}. See the per-element
 * methods in {@link ElementBuilders} for accepted parameter keys.
 */
public final class AsciiSceneFactory {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private AsciiSceneFactory() {}

    // ── parse ────────────────────────────────────────────────────────

    /**
     * Build a {@link SceneSpec} from a raw {@code Map<String, Object>}
     * (typically the deserialised top-level JSON object).
     *
     * @throws SceneSpecException if {@code width}/{@code height} are
     *         missing or non-positive, or {@code elements} is not a
     *         list, or any element entry is malformed.
     */
    public static SceneSpec fromMap(Map<String, Object> raw) {
        if (raw == null) {
            throw SceneSpecException.canvas("Scene map must not be null.");
        }
        Integer width = asInteger(raw.get("width"));
        Integer height = asInteger(raw.get("height"));
        if (width == null || width <= 0) {
            throw SceneSpecException.canvas("Missing or non-positive 'width'.");
        }
        if (height == null || height <= 0) {
            throw SceneSpecException.canvas("Missing or non-positive 'height'.");
        }
        Object rawElements = raw.get("elements");
        if (rawElements != null && !(rawElements instanceof List<?>)) {
            throw SceneSpecException.canvas("'elements' must be a list of element objects.");
        }
        List<?> elementsList = rawElements == null ? List.of() : (List<?>) rawElements;
        List<ElementSpec> elements = new ArrayList<>(elementsList.size());
        for (int i = 0; i < elementsList.size(); i++) {
            Object entry = elementsList.get(i);
            if (!(entry instanceof Map<?, ?> spec)) {
                throw SceneSpecException.element(i, null, "is not an object.");
            }
            Object typeRaw = spec.get("type");
            if (!(typeRaw instanceof String type) || type.isBlank()) {
                throw SceneSpecException.element(i, null, "is missing a 'type' string.");
            }
            Object paramsRaw = spec.get("params");
            Map<String, Object> params = paramsRaw instanceof Map<?, ?> m
                    ? castStringObjectMap(m) : Map.of();
            elements.add(new ElementSpec(type, params));
        }
        boolean trim = !raw.containsKey("trim") || Boolean.TRUE.equals(raw.get("trim"));
        Integer padding = asInteger(raw.get("padding"));
        int paddingValue = padding == null ? 0 : padding;
        return new SceneSpec(width, height, elements, trim, paddingValue);
    }

    /**
     * Parse a JSON scene document into a {@link SceneSpec}. Throws
     * {@link SceneSpecException} for both malformed JSON and structural
     * issues in the parsed map.
     */
    public static SceneSpec fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw SceneSpecException.canvas("Scene JSON must not be empty.");
        }
        Map<String, Object> raw;
        try {
            raw = JSON.readValue(json, MAP_TYPE);
        } catch (JacksonException e) {
            throw SceneSpecException.canvas("Malformed scene JSON: " + e.getMessage(), e);
        }
        return fromMap(raw);
    }

    // ── render ───────────────────────────────────────────────────────

    /**
     * Build the rendered {@link IContext} for {@code scene} without
     * drawing it. Useful for callers that want to add further elements
     * before rendering.
     */
    public static IContext toContext(SceneSpec scene) {
        IContextBuilder builder = Render.builder()
                .width(scene.width())
                .height(scene.height())
                .layer(new Region(0, 0, scene.width(), scene.height()));
        List<ElementSpec> elements = scene.elements();
        for (int i = 0; i < elements.size(); i++) {
            ElementSpec spec = elements.get(i);
            IElement element;
            try {
                element = ElementBuilders.dispatch(spec.type(), spec.params());
            } catch (IllegalArgumentException e) {
                throw SceneSpecException.element(i, spec.type(), e.getMessage(), e);
            }
            if (element == null) {
                throw SceneSpecException.element(i, spec.type(),
                        "Unknown element type '" + spec.type() + "'.");
            }
            builder.element(element);
        }
        return builder.build();
    }

    /** Render a parsed {@link SceneSpec} to ASCII text. */
    public static String render(SceneSpec scene) {
        IContext ctx = toContext(scene);
        ICanvas canvas = new Render().render(ctx);
        return scene.trim() ? canvas.trim().getText() : canvas.getText();
    }

    /** Convenience: {@code render(fromMap(raw))}. */
    public static String render(Map<String, Object> raw) {
        return render(fromMap(raw));
    }

    /** Convenience: {@code render(fromJson(json))}. */
    public static String renderJson(String json) {
        return render(fromJson(json));
    }
}
