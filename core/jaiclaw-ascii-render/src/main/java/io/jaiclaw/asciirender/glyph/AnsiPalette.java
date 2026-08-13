package io.jaiclaw.asciirender.glyph;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Optional post-render colorizer. Walks a plain-text rendered scene
 * and wraps every occurrence of a registered glyph in ANSI foreground
 * codes matching its {@link GlyphSemanticClass}.
 *
 * <p>This is a separate pass over the rendered text — the
 * {@code Canvas} itself stays purely char-per-cell (no attribute
 * plane). Rendered scenes therefore work identically in Markdown docs
 * (no colors), plain log files (no colors), and colour-capable
 * terminals (colored when {@link AnsiSupport#isColorEnabled()} returns
 * true).
 *
 * <p>Palette values use the exact same ANSI foreground escapes as
 * {@code install.sh}, {@code JaiClaw.java}, and {@code bin/jaiclaw}
 * so shell + Java output look identical when both write to the same
 * terminal.
 *
 * <p>Multi-codepoint glyphs are handled correctly: the palette scans
 * the rendered text longest-glyph-first so a two-codepoint entry
 * (e.g. an emoji with a variation selector) wins over a single-char
 * one that happens to be a prefix.
 */
public final class AnsiPalette {

    /** ANSI reset — restore default foreground / attributes. */
    public static final String RESET = "\033[0m";

    private final Map<GlyphSemanticClass, String> codes;
    private final boolean colorEnabled;

    private AnsiPalette(Map<GlyphSemanticClass, String> codes, boolean colorEnabled) {
        this.codes = new EnumMap<>(codes);
        this.colorEnabled = colorEnabled;
    }

    /**
     * The default JaiClaw palette — matches the colors in
     * {@code install.sh} and {@code JaiClaw.java}. Color-enabled state
     * is inherited from {@link AnsiSupport#isColorEnabled()}.
     */
    public static AnsiPalette defaultPalette() {
        Map<GlyphSemanticClass, String> defaults = new EnumMap<>(GlyphSemanticClass.class);
        defaults.put(GlyphSemanticClass.SUCCESS,    "\033[0;32m");   // green
        defaults.put(GlyphSemanticClass.ERROR,      "\033[0;31m");   // red
        defaults.put(GlyphSemanticClass.WARNING,    "\033[1;33m");   // bold yellow
        defaults.put(GlyphSemanticClass.INFO,       "\033[0;36m");   // cyan
        defaults.put(GlyphSemanticClass.NEUTRAL,    "");             // no color
        defaults.put(GlyphSemanticClass.DECORATIVE, "");
        return new AnsiPalette(defaults, AnsiSupport.isColorEnabled());
    }

    /**
     * A palette identical to {@link #defaultPalette()} but with the
     * color-enabled flag pinned to {@code enabled} regardless of the
     * runtime TTY / {@code NO_COLOR} state. Useful for tests that
     * assert on the colored output shape and for adopters that want
     * to force color on / off explicitly.
     */
    public AnsiPalette withColorEnabled(boolean enabled) {
        return new AnsiPalette(this.codes, enabled);
    }

    /**
     * Override the ANSI code for a single semantic class. Returns a
     * new palette; the original is unchanged.
     */
    public AnsiPalette withCode(GlyphSemanticClass semanticClass, String ansiCode) {
        Map<GlyphSemanticClass, String> next = new EnumMap<>(this.codes);
        next.put(semanticClass, ansiCode == null ? "" : ansiCode);
        return new AnsiPalette(next, this.colorEnabled);
    }

    public boolean isColorEnabled() {
        return colorEnabled;
    }

    /**
     * Wrap every occurrence of a glyph in {@code registry} with the
     * ANSI code for its semantic class. Returns {@code rendered}
     * unchanged when {@link #isColorEnabled()} is {@code false} or
     * when no glyph in the registry matches anything in the text.
     *
     * @param rendered the plain-text output from
     *                 {@link io.jaiclaw.asciirender.factory.AsciiSceneFactory#render}
     * @param registry the registry whose glyphs should be colorized
     */
    public String colorize(String rendered, GlyphRegistry registry) {
        if (!colorEnabled || rendered == null || rendered.isEmpty() || registry == null) {
            return rendered;
        }
        // Sort glyphs by length descending so multi-codepoint glyphs
        // win over single-char prefixes. Deduplicate on glyph text so
        // aliased entries ({"ok","check"} both mapping to "✓") only
        // wrap once per position.
        LinkedHashSet<GlyphDefinition> seen = new LinkedHashSet<>();
        for (GlyphDefinition g : registry.list()) {
            if (!g.glyph().isEmpty()) seen.add(g);
        }
        List<GlyphDefinition> ordered = seen.stream()
                .sorted(Comparator.comparingInt((GlyphDefinition g) -> g.glyph().length()).reversed())
                .toList();

        StringBuilder out = new StringBuilder(rendered.length() + 32);
        int i = 0;
        outer:
        while (i < rendered.length()) {
            for (GlyphDefinition g : ordered) {
                String glyph = g.glyph();
                if (rendered.regionMatches(i, glyph, 0, glyph.length())) {
                    String code = codes.getOrDefault(g.semanticClass(), "");
                    if (code.isEmpty()) {
                        out.append(glyph);
                    } else {
                        out.append(code).append(glyph).append(RESET);
                    }
                    i += glyph.length();
                    continue outer;
                }
            }
            out.append(rendered.charAt(i));
            i++;
        }
        return out.toString();
    }

    /**
     * Snapshot of the current class-to-code table. Unmodifiable.
     * Useful for debugging + tests.
     */
    public Map<GlyphSemanticClass, String> codes() {
        return java.util.Collections.unmodifiableMap(codes);
    }

    /** {@code true} when {@code palette.colorize} would change any of {@code registry}'s glyphs. */
    public boolean wouldColorize(Collection<GlyphDefinition> glyphs) {
        if (!colorEnabled || glyphs == null) return false;
        for (GlyphDefinition g : glyphs) {
            String code = codes.get(g.semanticClass());
            if (code != null && !code.isEmpty()) return true;
        }
        return false;
    }
}
