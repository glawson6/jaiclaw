package io.jaiclaw.tools.builtin

import groovy.json.JsonSlurper
import io.jaiclaw.asciirender.profile.AsciiRenderProfile
import io.jaiclaw.asciirender.profile.AsciiRenderProfiles
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

/**
 * Profile-aware execution paths for {@link AsciiBoxTool} (0.9.1).
 */
class AsciiBoxToolProfileSpec extends Specification {

    AsciiBoxTool tool = new AsciiBoxTool()

    def cleanup() {
        AsciiRenderProfiles.registerBuiltIns()
    }

    def "input schema declares profile and padding"() {
        when:
        def parsed = new JsonSlurper().parseText(tool.definition().inputSchema())

        then:
        parsed.properties.profile.type == "string"
        parsed.properties.padding.type == "integer"
    }

    def "profile=telegram_mobile yields a narrow box (width 30)"() {
        when:
        ToolResult result = tool.execute([content: "hi", profile: "telegram_mobile"], null)

        then:
        result instanceof ToolResult.Success
        // 30 inner + 2 border = 32 columns total; padding 0 so no inner margin.
        def line0 = ((ToolResult.Success) result).content.split("\n", -1)[0]
        line0.length() == 32
    }

    def "profile=slack_desktop yields a wider box (width 100)"() {
        when:
        ToolResult result = tool.execute([content: "hi", profile: "slack_desktop"], null)

        then:
        result instanceof ToolResult.Success
        def line0 = ((ToolResult.Success) result).content.split("\n", -1)[0]
        // 100 inner + 2 border + 2*1 padding = 104 columns total.
        line0.length() == 104
    }

    def "explicit width overrides the profile's width"() {
        when:
        ToolResult result = tool.execute(
                [content: "hi", profile: "telegram_mobile", width: 50], null)

        then:
        result instanceof ToolResult.Success
        def line0 = ((ToolResult.Success) result).content.split("\n", -1)[0]
        // 50 inner + 2 border + 0 padding = 52 columns total.
        line0.length() == 52
    }

    def "explicit padding overrides the profile's padding"() {
        when:
        ToolResult result = tool.execute(
                [content: "hi", profile: "telegram_mobile", padding: 2], null)

        then:
        result instanceof ToolResult.Success
        // 30 inner + 2 border + 4 padding = 36 columns; box is taller because of padding.
        def lines = ((ToolResult.Success) result).content.split("\n", -1)
        lines[0].length() == 36
        lines.length >= 5   // 1 top border + 2 padding + 1 content + 2 padding (clipped at content) + 1 bottom
    }

    def "no profile uses the deployment default (shell_80, width 78)"() {
        when:
        ToolResult result = tool.execute([content: "hi"], null)

        then:
        result instanceof ToolResult.Success
        def line0 = ((ToolResult.Success) result).content.split("\n", -1)[0]
        // 78 inner + 2 border + 2*1 padding = 82 columns.
        line0.length() == 82
    }

    def "unknown profile name falls back to default (no error)"() {
        when:
        ToolResult result = tool.execute(
                [content: "hi", profile: "bogus_name"], null)

        then:
        result instanceof ToolResult.Success
        def line0 = ((ToolResult.Success) result).content.split("\n", -1)[0]
        line0.length() == 82   // same as default fallback
    }

    def "operator-registered profile is honored"() {
        given:
        AsciiRenderProfiles.register(new AsciiRenderProfile("iphone_bigtext", 24, 0))

        when:
        ToolResult result = tool.execute([content: "hi", profile: "iphone_bigtext"], null)

        then:
        result instanceof ToolResult.Success
        def line0 = ((ToolResult.Success) result).content.split("\n", -1)[0]
        // 24 inner + 2 border + 0 padding = 26.
        line0.length() == 26
    }
}
