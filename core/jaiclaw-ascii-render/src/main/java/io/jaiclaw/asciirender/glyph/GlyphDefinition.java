package io.jaiclaw.asciirender.glyph;

import java.util.Objects;

/**
 * The addressable unit adopters register with a {@link GlyphRegistry}.
 * Pairs a canonical {@code name} with the Unicode glyph itself + a
 * {@link GlyphSemanticClass} used for color theming.
 *
 * <p>{@code glyph} is a {@link String} rather than a {@code char} so
 * multi-codepoint glyphs — combining sequences, emoji with variation
 * selectors — work without a wrapper.
 *
 * @param name           kebab-case identifier used by
 *                       {@code {"type":"glyph","params":{"name":"..."}}}
 *                       scene specs
 * @param glyph          the Unicode text to write to the canvas
 * @param semanticClass  category consumed by {@link AnsiPalette}
 * @param description    short human-facing description for docs +
 *                       tool-list surfaces; may be empty
 */
public record GlyphDefinition(
        String name,
        String glyph,
        GlyphSemanticClass semanticClass,
        String description) {

    public GlyphDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(glyph, "glyph");
        Objects.requireNonNull(semanticClass, "semanticClass");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Glyph name must not be blank.");
        }
        if (glyph.isEmpty()) {
            throw new IllegalArgumentException("Glyph text must not be empty.");
        }
        if (description == null) {
            description = "";
        }
    }
}
