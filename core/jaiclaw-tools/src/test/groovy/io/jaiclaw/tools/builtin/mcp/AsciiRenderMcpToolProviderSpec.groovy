package io.jaiclaw.tools.builtin.mcp

import io.jaiclaw.core.mcp.McpToolResult
import io.jaiclaw.tools.builtin.AsciiBoxTool
import io.jaiclaw.tools.builtin.AsciiRenderTool
import spock.lang.Specification

class AsciiRenderMcpToolProviderSpec extends Specification {

    AsciiRenderMcpToolProvider provider = new AsciiRenderMcpToolProvider()

    def "server name and description identify the renderer"() {
        expect:
        provider.getServerName() == "ascii-render"
        provider.getServerDescription()
                .toLowerCase()
                .contains("ascii")
    }

    def "exposes both ascii_render and ascii_box tools"() {
        when:
        def tools = provider.getTools()

        then:
        tools*.name().toSet() == ["ascii_render", "ascii_box"] as Set
        tools.every { it.inputSchema().contains('"type": "object"') }
    }

    def "unknown tool returns an error result"() {
        when:
        McpToolResult result = provider.execute("not_a_tool", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }

    def "ascii_box delegates and returns rendered text on success"() {
        when:
        McpToolResult result = provider.execute("ascii_box", [content: "hi"], null)

        then:
        !result.isError()
        result.content().contains("hi")
    }

    def "ascii_render error from underlying tool surfaces as MCP error"() {
        when:
        McpToolResult result = provider.execute("ascii_render", [width: 10, elements: []], null)

        then:
        result.isError()
    }

    def "MCP execute returns the same rendered text as the in-process built-in"() {
        given:
        AsciiBoxTool boxTool = new AsciiBoxTool()
        Map<String, Object> args = [content: "hello", border: "double", title: "MSG"]

        when:
        def mcpResult = provider.execute("ascii_box", args, null)
        def builtinResult = boxTool.execute(args, null)

        then:
        !mcpResult.isError()
        builtinResult instanceof io.jaiclaw.core.tool.ToolResult.Success
        mcpResult.content() == ((io.jaiclaw.core.tool.ToolResult.Success) builtinResult).content()
    }

    def "MCP tool schema matches the built-in tool schema verbatim"() {
        given:
        AsciiRenderTool renderTool = new AsciiRenderTool()
        AsciiBoxTool boxTool = new AsciiBoxTool()

        when:
        def tools = provider.getTools()
        def renderDef = tools.find { it.name() == "ascii_render" }
        def boxDef = tools.find { it.name() == "ascii_box" }

        then:
        renderDef.inputSchema() == renderTool.definition().inputSchema()
        boxDef.inputSchema() == boxTool.definition().inputSchema()
    }
}
