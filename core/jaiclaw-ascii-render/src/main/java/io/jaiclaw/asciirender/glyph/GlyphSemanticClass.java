package io.jaiclaw.asciirender.glyph;

/**
 * Abstract category assigned to a glyph so a palette can theme without
 * hardcoding a color per glyph name. {@link AnsiPalette} maps each
 * class to an ANSI foreground code; adopters that install a custom
 * palette can pick any mapping they like.
 *
 * <p>The vocabulary is deliberately small — enough to distinguish
 * success from failure from warning without turning into a color
 * catalogue.
 */
public enum GlyphSemanticClass {
    SUCCESS,
    WARNING,
    ERROR,
    INFO,
    NEUTRAL,
    DECORATIVE
}
