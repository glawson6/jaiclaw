package io.jaiclaw.asciirender.element

import io.jaiclaw.asciirender.factory.AsciiSceneFactory
import io.jaiclaw.asciirender.glyph.GlyphDefinition
import io.jaiclaw.asciirender.glyph.GlyphRegistry
import io.jaiclaw.asciirender.glyph.GlyphSemanticClass
import spock.lang.Specification

class GlyphSpec extends Specification {

    def cleanup() {
        GlyphRegistry.resetGlobal()
    }

    def "name-lookup renders the registered glyph at (x, y)"() {
        given:
        GlyphRegistry registry = GlyphRegistry.defaults()
        GlyphRegistry.setGlobal(registry)

        when:
        String rendered = AsciiSceneFactory.render([
                width   : 10,
                height  : 3,
                trim    : false,
                elements: [
                        [type: "glyph", params: [x: 2, y: 1, name: "ok"]]
                ]
        ])
        List<String> lines = rendered.split("\n") as List

        then:
        lines[1].charAt(2) == '✓' as char        // ✓ U+2713
    }

    def "literal glyph + semanticClass renders the raw character"() {
        when:
        String rendered = AsciiSceneFactory.render([
                width   : 6,
                height  : 2,
                trim    : false,
                elements: [
                        [type: "glyph", params: [x: 1, y: 0, glyph: "★", semanticClass: "INFO"]]
                ]
        ])

        then:
        rendered.contains("★")
    }

    def "unknown glyph name surfaces the known-glyph list in the error"() {
        given:
        GlyphRegistry registry = GlyphRegistry.defaults()
        GlyphRegistry.setGlobal(registry)

        when:
        AsciiSceneFactory.render([
                width   : 6,
                height  : 2,
                elements: [
                        [type: "glyph", params: [x: 0, y: 0, name: "not-a-real-glyph"]]
                ]
        ])

        then:
        Throwable ex = thrown()
        ex.message.contains("not-a-real-glyph")
        ex.message.contains("ok")
    }

    def "missing both name and literal glyph fails clearly"() {
        when:
        AsciiSceneFactory.render([
                width   : 6,
                height  : 2,
                elements: [
                        [type: "glyph", params: [x: 0, y: 0]]
                ]
        ])

        then:
        Throwable ex = thrown()
        ex.message.contains("name")
        ex.message.contains("glyph")
    }

    def "adopter-registered glyph wins over the built-in when name collides"() {
        given:
        GlyphRegistry registry = GlyphRegistry.defaults()
        registry.register(new GlyphDefinition("ok", "✅",
                GlyphSemanticClass.SUCCESS, "Emoji check mark"))
        GlyphRegistry.setGlobal(registry)

        when:
        String rendered = AsciiSceneFactory.render([
                width   : 4,
                height  : 2,
                elements: [
                        [type: "glyph", params: [x: 0, y: 0, name: "ok"]]
                ]
        ])

        then:
        rendered.contains("✅")
        !rendered.contains("✓")
    }
}
