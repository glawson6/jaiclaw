package io.jaiclaw.asciirender.glyph

import spock.lang.Specification

class AnsiPaletteSpec extends Specification {

    def "color-disabled palette returns input unchanged"() {
        given:
        AnsiPalette palette = AnsiPalette.defaultPalette().withColorEnabled(false)
        GlyphRegistry registry = GlyphRegistry.defaults()

        when:
        String out = palette.colorize("✓ done", registry)

        then:
        out == "✓ done"
    }

    def "SUCCESS glyphs get wrapped in the green ANSI code"() {
        given:
        AnsiPalette palette = AnsiPalette.defaultPalette().withColorEnabled(true)
        GlyphRegistry registry = GlyphRegistry.defaults()

        when:
        String out = palette.colorize("✓ ok", registry)

        then:
        out == "\033[0;32m✓\033[0m ok"
    }

    def "ERROR glyphs get wrapped in the red ANSI code"() {
        given:
        AnsiPalette palette = AnsiPalette.defaultPalette().withColorEnabled(true)
        GlyphRegistry registry = GlyphRegistry.defaults()

        when:
        String out = palette.colorize("✗ boom", registry)

        then:
        out == "\033[0;31m✗\033[0m boom"
    }

    def "NEUTRAL glyphs are recognised but not colour-wrapped"() {
        given:
        AnsiPalette palette = AnsiPalette.defaultPalette().withColorEnabled(true)
        GlyphRegistry registry = GlyphRegistry.defaults()

        when:
        String out = palette.colorize("● item", registry)

        then:
        out == "● item"
    }

    def "colorize handles multiple glyphs and preserves surrounding text"() {
        given:
        AnsiPalette palette = AnsiPalette.defaultPalette().withColorEnabled(true)
        GlyphRegistry registry = GlyphRegistry.defaults()

        when:
        String out = palette.colorize("A ✓ B ✗ C", registry)

        then:
        out == "A \033[0;32m✓\033[0m B \033[0;31m✗\033[0m C"
    }

    def "withCode() overrides the palette without mutating the original"() {
        given:
        AnsiPalette base = AnsiPalette.defaultPalette().withColorEnabled(true)

        when:
        AnsiPalette bright = base.withCode(GlyphSemanticClass.SUCCESS, "\033[1;92m")

        then:
        bright.codes().get(GlyphSemanticClass.SUCCESS) == "\033[1;92m"
        base.codes().get(GlyphSemanticClass.SUCCESS) == "\033[0;32m"
    }

    def "null or empty input returns as-is"() {
        given:
        AnsiPalette palette = AnsiPalette.defaultPalette().withColorEnabled(true)
        GlyphRegistry registry = GlyphRegistry.defaults()

        expect:
        palette.colorize(null, registry) == null
        palette.colorize("", registry) == ""
    }

    def "unknown glyphs pass through unchanged"() {
        given:
        AnsiPalette palette = AnsiPalette.defaultPalette().withColorEnabled(true)
        GlyphRegistry registry = new GlyphRegistry()   // empty

        when:
        String out = palette.colorize("✓ ✗ ●", registry)

        then:
        out == "✓ ✗ ●"
    }
}
