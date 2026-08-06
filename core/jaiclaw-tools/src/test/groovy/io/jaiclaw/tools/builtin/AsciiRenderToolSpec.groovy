package io.jaiclaw.tools.builtin

import groovy.json.JsonSlurper
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

class AsciiRenderToolSpec extends Specification {

    AsciiRenderTool tool = new AsciiRenderTool()

    def "definition reports name, section and rendering-friendly profiles"() {
        given:
        def def_ = tool.definition()

        expect:
        def_.name() == "ascii_render"
        def_.section() == "Rendering"
        def_.profiles().containsAll([ToolProfile.FULL, ToolProfile.CODING, ToolProfile.MESSAGING])
    }

    def "input schema declares the three required keys"() {
        expect:
        def schema = tool.definition().inputSchema()
        schema.contains('"required"')
        schema.contains('"width"')
        schema.contains('"height"')
        schema.contains('"elements"')
    }

    def "input schema parses as well-formed JSON with the expected required array"() {
        when:
        def parsed = new JsonSlurper().parseText(tool.definition().inputSchema())

        then:
        parsed.type == "object"
        // width is no longer required at the JSON layer — the profile
        // (or its default fallback) supplies it when the LLM omits it.
        parsed.required as Set == ["height", "elements"] as Set
        parsed.get("properties").get("width").get("type") == "integer"
        parsed.get("properties").get("height").get("type") == "integer"
        parsed.get("properties").get("elements").get("type") == "array"
        parsed.get("properties").get("profile").get("type") == "string"
        parsed.get("properties").get("padding").get("type") == "integer"
    }

    def "renders a single rectangle covering the whole canvas"() {
        given:
        def params = [
                width   : 10,
                height  : 4,
                elements: [[type: "rectangle"]]
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Success
        def lines = ((ToolResult.Success) result).content.split("\n", -1)
        // Trimmed canvas: a 10x4 rectangle drawn with single-line glyphs.
        lines[0].startsWith("┌")
        lines[0].endsWith("┐")
        lines[lines.length - 1].startsWith("└")
        lines[lines.length - 1].endsWith("┘")
    }

    def "renders a labelled box with explicit coordinates"() {
        given:
        def params = [
                width   : 20,
                height  : 5,
                elements: [
                        [type: "rectangle", params: [x: 0, y: 0, width: 20, height: 5]],
                        [type: "label", params: [text: "hi", x: 8, y: 2]]
                ]
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content.contains("hi")
    }

    def "unknown element type produces an error result"() {
        given:
        def params = [
                width   : 10,
                height  : 4,
                elements: [[type: "marshmallow"]]
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message.contains("marshmallow")
    }

    def "missing width is supplied from the default profile"() {
        when: "no width, no profile — the deployment default (shell_80, width 78) fills it in"
        ToolResult result = tool.execute([height: 4, elements: []], null)

        then:
        result instanceof ToolResult.Success
    }

    def "missing height is still rejected"() {
        when: "height has no profile-supplied default and is still required"
        ToolResult result = tool.execute([width: 40, elements: []], null)

        then:
        result instanceof ToolResult.Error
    }

    def "altText field appears in the input schema (Section 508 / WCAG 2.0 AA)"() {
        when:
        def parsed = new JsonSlurper().parseText(tool.definition().inputSchema())

        then:
        parsed.get("properties").get("altText") != null
        parsed.get("properties").get("altText").get("type") == "string"
        // Alt text is optional — decorative diagrams don't require it under WCAG
        (parsed.required as Set).contains("altText") == false
    }

    def "altText round-trips into ToolResult.Success metadata when supplied"() {
        given:
        def params = [
                width  : 8,
                height : 3,
                elements: [[type: "rectangle", params: [x: 0, y: 0, w: 8, h: 3]]],
                altText: "An 8x3 rectangle framing the canvas"
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Success
        def success = result as ToolResult.Success
        success.metadata().get("altText") == "An 8x3 rectangle framing the canvas"
    }

    def "altText absent from metadata when the LLM omits it"() {
        given:
        def params = [
                width  : 8,
                height : 3,
                elements: [[type: "rectangle", params: [x: 0, y: 0, w: 8, h: 3]]]
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Success
        def success = result as ToolResult.Success
        // No altText → metadata is the empty default (no altText key)
        !success.metadata().containsKey("altText")
    }

    def "blank altText is treated as absent"() {
        given:
        def params = [
                width  : 8,
                height : 3,
                elements: [[type: "rectangle", params: [x: 0, y: 0, w: 8, h: 3]]],
                altText: "   "
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Success
        def success = result as ToolResult.Success
        !success.metadata().containsKey("altText")
    }
}
