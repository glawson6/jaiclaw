package io.jaiclaw.asciirender.glyph;

import java.util.List;

/**
 * SPI adopters implement (as a Spring {@code @Bean}) to register
 * additional glyphs with the shared {@link GlyphRegistry}. Discovered
 * during framework auto-configuration; the returned definitions are
 * merged in over the built-in {@link GlyphSet#defaults()} with
 * last-write-wins semantics.
 *
 * <p>Example:
 * <pre>{@code
 * @Bean
 * GlyphContribution appGlyphs() {
 *     return () -> List.of(
 *         new GlyphDefinition("pass",  "✅",
 *             GlyphSemanticClass.SUCCESS, "Emoji check mark"),
 *         new GlyphDefinition("onprem", "🏢",
 *             GlyphSemanticClass.NEUTRAL, "On-premises building")
 *     );
 * }
 * }</pre>
 *
 * <p>Overriding a built-in name is legitimate — e.g. an adopter can
 * remap {@code ok} to {@code ✅} — and is logged at INFO so operators
 * can trace visual regressions back to the responsible bean.
 */
@FunctionalInterface
public interface GlyphContribution {

    /** Glyphs to add to the shared registry. Never {@code null}. */
    List<GlyphDefinition> glyphs();
}
