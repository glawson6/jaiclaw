package io.jaiclaw.asciirender.glyph

import spock.lang.Specification

class GlyphRegistrySpec extends Specification {

    def "defaults() seeds the built-in vocabulary"() {
        given:
        GlyphRegistry registry = GlyphRegistry.defaults()

        expect:
        registry.size() == GlyphSet.defaults().size()
        registry.resolve("ok").isPresent()
        registry.resolve("ok").get().glyph() == "✓"
        registry.resolve("fail").get().semanticClass() == GlyphSemanticClass.ERROR
    }

    def "resolve() returns empty for unknown names"() {
        expect:
        GlyphRegistry.defaults().resolve("no-such-glyph").isEmpty()
        GlyphRegistry.defaults().resolve(null).isEmpty()
    }

    def "register() overrides an existing entry (last-writer-wins)"() {
        given:
        GlyphRegistry registry = GlyphRegistry.defaults()

        when:
        registry.register(new GlyphDefinition("ok", "🟢",
                GlyphSemanticClass.SUCCESS, "circle"))

        then:
        registry.resolve("ok").get().glyph() == "🟢"
        // Size unchanged — override replaces, doesn't append.
        registry.size() == GlyphSet.defaults().size()
    }

    def "register() with a brand-new name grows the registry"() {
        given:
        GlyphRegistry registry = GlyphRegistry.defaults()
        int before = registry.size()

        when:
        registry.register(new GlyphDefinition("thunder", "⚡",
                GlyphSemanticClass.INFO, "Fast path"))

        then:
        registry.size() == before + 1
        registry.resolve("thunder").get().glyph() == "⚡"
    }

    def "names() preserves insertion order"() {
        given:
        GlyphRegistry registry = new GlyphRegistry()

        when:
        registry.register(new GlyphDefinition("gamma", "γ", GlyphSemanticClass.INFO, ""))
        registry.register(new GlyphDefinition("alpha", "α", GlyphSemanticClass.INFO, ""))
        registry.register(new GlyphDefinition("beta",  "β", GlyphSemanticClass.INFO, ""))

        then:
        registry.names() as List == ["gamma", "alpha", "beta"]
    }

    def "GlyphDefinition rejects blank name / empty glyph"() {
        when:
        new GlyphDefinition("", "x", GlyphSemanticClass.NEUTRAL, "")

        then:
        thrown(IllegalArgumentException)

        when:
        new GlyphDefinition("ok", "", GlyphSemanticClass.NEUTRAL, "")

        then:
        thrown(IllegalArgumentException)
    }

    def "global() default returns a defaults-seeded registry"() {
        given:
        GlyphRegistry.resetGlobal()

        expect:
        GlyphRegistry.global().resolve("ok").isPresent()
    }

    def "setGlobal() replaces the shared registry"() {
        given:
        GlyphRegistry replacement = new GlyphRegistry()
        replacement.register(new GlyphDefinition("only",
                "X", GlyphSemanticClass.INFO, ""))

        when:
        GlyphRegistry.setGlobal(replacement)

        then:
        GlyphRegistry.global().size() == 1
        GlyphRegistry.global().resolve("only").isPresent()
        GlyphRegistry.global().resolve("ok").isEmpty()

        cleanup:
        GlyphRegistry.resetGlobal()
    }
}
