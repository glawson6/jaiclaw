package io.jaiclaw.asciirender.element;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.core.Point;
import io.jaiclaw.asciirender.glyph.GlyphDefinition;
import io.jaiclaw.asciirender.glyph.GlyphRegistry;
import io.jaiclaw.asciirender.glyph.GlyphSemanticClass;

import java.util.Objects;

/**
 * Named glyph rendered at (x, y) — status markers, bullets, arrows,
 * anything from the shared {@link GlyphRegistry}. The rendered output
 * is a plain Unicode string; color decoration is a separate opt-in
 * pass via {@link io.jaiclaw.asciirender.glyph.AnsiPalette}.
 *
 * <p>Two construction paths:
 * <ul>
 *   <li>{@link #byName(String, int, int, GlyphRegistry)} — look the
 *       glyph up in a registry. The typical path for scene-spec
 *       builders that resolve {@code {"name":"ok"}} at draw time.</li>
 *   <li>{@link #Glyph(int, int, String, GlyphSemanticClass)} — supply
 *       a raw glyph string + semantic class directly. Handy for one-
 *       off cases that don't warrant a registration.</li>
 * </ul>
 */
public class Glyph implements IElement {

    private final int x;
    private final int y;
    private final String glyph;
    private final GlyphSemanticClass semanticClass;

    /**
     * Construct a Glyph with an explicit character + semantic class.
     * Prefer {@link #byName(String, int, int, GlyphRegistry)} when the
     * glyph is registered — that path keeps names authoritative.
     */
    public Glyph(int x, int y, String glyph, GlyphSemanticClass semanticClass) {
        this.x = x;
        this.y = y;
        this.glyph = Objects.requireNonNull(glyph, "glyph");
        this.semanticClass = semanticClass == null ? GlyphSemanticClass.DECORATIVE : semanticClass;
        if (glyph.isEmpty()) {
            throw new IllegalArgumentException("Glyph text must not be empty.");
        }
    }

    /**
     * Look up {@code name} in {@code registry} and return a Glyph
     * positioned at (x, y). Throws {@link IllegalArgumentException}
     * when the name is unknown — the element builder catches this and
     * surfaces a scene-level error with the element index.
     */
    public static Glyph byName(String name, int x, int y, GlyphRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        GlyphDefinition def = registry.resolve(name).orElseThrow(() -> new IllegalArgumentException(
                "Unknown glyph name '" + name + "'. Known: " + registry.names()));
        return new Glyph(x, y, def.glyph(), def.semanticClass());
    }

    public int getX()                             { return x; }
    public int getY()                             { return y; }
    public String getGlyph()                      { return glyph; }
    public GlyphSemanticClass getSemanticClass()  { return semanticClass; }

    @Override
    public IPoint draw(ICanvas canvas, IContext context) {
        canvas.draw(x, y, glyph);
        return new Point(x, y);
    }
}
