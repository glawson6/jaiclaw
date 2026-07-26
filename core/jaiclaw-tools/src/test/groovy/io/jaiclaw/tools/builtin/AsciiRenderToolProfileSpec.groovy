package io.jaiclaw.tools.builtin

import groovy.json.JsonSlurper
import io.jaiclaw.asciirender.profile.AsciiRenderProfile
import io.jaiclaw.asciirender.profile.AsciiRenderProfiles
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

/**
 * Profile-aware execution paths for {@link AsciiRenderTool} (0.9.1).
 */
class AsciiRenderToolProfileSpec extends Specification {

    AsciiRenderTool tool = new AsciiRenderTool()

    def cleanup() {
        AsciiRenderProfiles.registerBuiltIns()
    }

    def "input schema declares profile and padding"() {
        when:
        def parsed = new JsonSlurper().parseText(tool.definition().inputSchema())

        then:
        parsed.get("properties").get("profile").get("type") == "string"
        parsed.get("properties").get("padding").get("type") == "integer"
        // width is no longer required at the JSON layer (profile supplies it)
        parsed.required as Set == ["height", "elements"] as Set
    }

    def "profile supplies the canvas width when omitted"() {
        given: "telegram_mobile = width 30"
        def params = [
                profile : "telegram_mobile",
                height  : 4,
                elements: [[type: "rectangle"]]
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Success
        def lines = ((ToolResult.Success) result).content.split("\n", -1)
        // 30-wide canvas; rectangle covers the whole canvas.
        lines[0].length() == 30
    }

    def "explicit width overrides the profile's width"() {
        given:
        def params = [
                profile : "telegram_mobile",  // would yield 30
                width   : 50,
                height  : 4,
                elements: [[type: "rectangle"]]
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Success
        def lines = ((ToolResult.Success) result).content.split("\n", -1)
        lines[0].length() == 50
    }

    def "no profile, no width — deployment default applies"() {
        given: "shell_80 = width 78"
        def params = [
                height  : 4,
                elements: [[type: "rectangle"]]
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Success
        def lines = ((ToolResult.Success) result).content.split("\n", -1)
        lines[0].length() == 78
    }

    def "operator-registered profile supplies width"() {
        given:
        AsciiRenderProfiles.register(new AsciiRenderProfile("iphone_bigtext", 24, 0))

        when:
        ToolResult result = tool.execute(
                [profile: "iphone_bigtext", height: 4, elements: [[type: "rectangle"]]], null)

        then:
        result instanceof ToolResult.Success
        def lines = ((ToolResult.Success) result).content.split("\n", -1)
        lines[0].length() == 24
    }

    def "unknown profile falls back to default"() {
        given:
        def params = [
                profile : "made_up",
                height  : 4,
                elements: [[type: "rectangle"]]
        ]

        when:
        ToolResult result = tool.execute(params, null)

        then:
        result instanceof ToolResult.Success
        def lines = ((ToolResult.Success) result).content.split("\n", -1)
        lines[0].length() == 78   // shell_80 default
    }
}
