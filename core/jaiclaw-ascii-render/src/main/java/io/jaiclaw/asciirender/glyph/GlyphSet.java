package io.jaiclaw.asciirender.glyph;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static default glyph vocabulary — the palette every fresh
 * {@link GlyphRegistry} starts with. Same shape as
 * {@code AsciiBox.Style}: a curated first-party set with a small
 * lookup surface and no plans to grow past a couple dozen entries.
 *
 * <p>The set mirrors what {@code install.sh}, {@code JaiClaw.java},
 * and {@code bin/jaiclaw} already emit so a scene rendered from
 * these defaults looks visually consistent with the rest of the
 * JaiClaw shell surface.
 *
 * <p>Common aliases ship as separate entries pointing at the same
 * glyph (e.g. {@code ok} + {@code check} both resolve to {@code ✓})
 * so scene authors don't have to remember one canonical name.
 */
public final class GlyphSet {

    private GlyphSet() {}

    private static final Map<String, GlyphDefinition> DEFAULTS = buildDefaults();

    /**
     * Return the built-in glyph vocabulary as an unmodifiable view.
     * Insertion order is stable — callers can iterate for doc /
     * tool-list generation.
     */
    public static Collection<GlyphDefinition> defaults() {
        return DEFAULTS.values();
    }

    /**
     * Look up a default glyph by name. Returns {@code null} when the
     * name is not part of the built-in set — callers should consult a
     * {@link GlyphRegistry} for adopter-contributed additions.
     */
    public static GlyphDefinition find(String name) {
        if (name == null) return null;
        return DEFAULTS.get(name);
    }

    private static Map<String, GlyphDefinition> buildDefaults() {
        LinkedHashMap<String, GlyphDefinition> map = new LinkedHashMap<>();
        for (GlyphDefinition g : List.of(
                new GlyphDefinition("ok",        "✓", GlyphSemanticClass.SUCCESS, "Success check mark"),
                new GlyphDefinition("check",     "✓", GlyphSemanticClass.SUCCESS, "Alias for 'ok'"),
                new GlyphDefinition("fail",      "✗", GlyphSemanticClass.ERROR,   "Failure cross mark"),
                new GlyphDefinition("cross",     "✗", GlyphSemanticClass.ERROR,   "Alias for 'fail'"),
                new GlyphDefinition("warn",      "!",      GlyphSemanticClass.WARNING, "Simple warning bang"),
                new GlyphDefinition("bang",      "!",      GlyphSemanticClass.WARNING, "Alias for 'warn'"),
                new GlyphDefinition("warning",   "⚠", GlyphSemanticClass.WARNING, "Warning triangle"),
                new GlyphDefinition("info",      "▸", GlyphSemanticClass.INFO,    "Right-pointing info arrowhead"),
                new GlyphDefinition("arrowhead", "▸", GlyphSemanticClass.INFO,    "Alias for 'info'"),
                new GlyphDefinition("arrow",     "▶", GlyphSemanticClass.INFO,    "Right-pointing arrow"),
                new GlyphDefinition("bullet",    "●", GlyphSemanticClass.NEUTRAL, "Filled bullet"),
                new GlyphDefinition("dot",       "●", GlyphSemanticClass.NEUTRAL, "Alias for 'bullet'"),
                new GlyphDefinition("star",      "★", GlyphSemanticClass.INFO,    "Filled star"),
                new GlyphDefinition("pending",   "⧗", GlyphSemanticClass.NEUTRAL, "Hourglass — task in progress"),
                new GlyphDefinition("hourglass", "⧗", GlyphSemanticClass.NEUTRAL, "Alias for 'pending'"),
                new GlyphDefinition("question",  "?",      GlyphSemanticClass.INFO,    "Unknown / prompt")
        )) {
            map.put(g.name(), g);
        }
        return map;
    }
}
