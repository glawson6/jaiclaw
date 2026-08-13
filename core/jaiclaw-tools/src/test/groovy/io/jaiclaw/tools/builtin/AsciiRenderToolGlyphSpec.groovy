package io.jaiclaw.tools.builtin

import io.jaiclaw.asciirender.glyph.GlyphDefinition
import io.jaiclaw.asciirender.glyph.GlyphRegistry
import io.jaiclaw.asciirender.glyph.GlyphSemanticClass
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

/**
 * End-to-end coverage that the schema description update + the shared
 * GlyphRegistry.global() plumbing agree with the ElementBuilders
 * dispatch and the underlying Canvas. The scene is intentionally tiny
 * so failures pinpoint a specific cell.
 */
class AsciiRenderToolGlyphSpec extends Specification {

    AsciiRenderTool tool = new AsciiRenderTool()

    def setup() {
        GlyphRegistry.setGlobal(GlyphRegistry.defaults())
    }

    def cleanup() {
        GlyphRegistry.resetGlobal()
    }

    def "input schema description advertises the glyph element type"() {
        expect:
        tool.definition().inputSchema().contains("glyph")
        tool.definition().description().contains("glyph")
    }

    def "renders a name-lookup glyph at the requested cell"() {
        given:
        def params = [
                width   : 8,
                height  : 3,
                trim    : false,
                elements: [
                        [type: "glyph", params: [x: 3, y: 1, name: "ok"]]
                ]
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Success
        def lines = ((ToolResult.Success) result).content.split("\n", -1)
        lines[1].charAt(3) == '✓' as char
    }

    def "renders a literal glyph without a name"() {
        given:
        def params = [
                width   : 6,
                height  : 2,
                elements: [
                        [type: "glyph", params: [x: 1, y: 0, glyph: "★", semanticClass: "INFO"]]
                ]
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content.contains("★")
    }

    def "unknown glyph name surfaces a clean tool-level Error result"() {
        given:
        def params = [
                width   : 6,
                height  : 2,
                elements: [
                        [type: "glyph", params: [x: 0, y: 0, name: "no-such-glyph"]]
                ]
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("no-such-glyph")
    }

    def "adopter-registered glyph is visible through the tool"() {
        given:
        GlyphRegistry.global().register(new GlyphDefinition("thunder", "⚡",
                GlyphSemanticClass.INFO, "Fast path"))
        def params = [
                width   : 6,
                height  : 2,
                elements: [
                        [type: "glyph", params: [x: 0, y: 0, name: "thunder"]]
                ]
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content.contains("⚡")
    }
}
