package io.jaiclaw.tools.builtin

import groovy.json.JsonSlurper
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

class AsciiBoxToolSpec extends Specification {

    AsciiBoxTool tool = new AsciiBoxTool()

    def "definition exposes name, rendering section and broad profiles"() {
        given:
        def def_ = tool.definition()

        expect:
        def_.name() == "ascii_box"
        def_.section() == "Rendering"
        def_.profiles().containsAll([ToolProfile.FULL, ToolProfile.CODING, ToolProfile.MESSAGING])
    }

    def "input schema parses as well-formed JSON with 'content' required"() {
        when:
        def parsed = new JsonSlurper().parseText(tool.definition().inputSchema())

        then:
        parsed.type == "object"
        parsed.required == ["content"]
        parsed.properties.content.type == "string"
    }

    def "single-line border wraps short content"() {
        when:
        ToolResult result = tool.execute([content: "hello", width: 20], null)

        then:
        result instanceof ToolResult.Success
        String text = ((ToolResult.Success) result).content
        text.startsWith("┌")
        text.contains("hello")
        text.contains("└")
    }

    def "double border style uses ╔ glyph"() {
        when:
        ToolResult result = tool.execute([content: "hi", border: "double"], null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content.startsWith("╔")
    }

    def "rounded border style uses ╭ glyph"() {
        when:
        ToolResult result = tool.execute([content: "hi", border: "rounded"], null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content.startsWith("╭")
    }

    def "bold border style uses ┏ glyph"() {
        when:
        ToolResult result = tool.execute([content: "hi", border: "bold"], null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content.startsWith("┏")
    }

    def "unknown border style falls back to single border (no error)"() {
        when:
        ToolResult result = tool.execute([content: "x", border: "plaid"], null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content.startsWith("┌")
    }

    def "title is rendered on the top edge"() {
        when:
        ToolResult result = tool.execute([content: "body", title: "NOTE", width: 30], null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content.contains("[ NOTE ]")
    }

    def "missing content is rejected"() {
        when:
        ToolResult result = tool.execute([:], null)

        then:
        result instanceof ToolResult.Error
    }

    def "wraps long lines at word boundaries"() {
        given:
        String content = "alpha beta gamma delta epsilon"

        when:
        ToolResult result = tool.execute([content: content, width: 10], null)

        then:
        result instanceof ToolResult.Success
        // Top border + at least 3 wrapped lines + bottom border for that content @ width 10
        ((ToolResult.Success) result).content.split("\n", -1).length >= 4
    }
}
