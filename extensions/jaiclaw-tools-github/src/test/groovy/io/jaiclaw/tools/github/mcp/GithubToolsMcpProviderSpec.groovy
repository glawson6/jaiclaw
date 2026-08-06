package io.jaiclaw.tools.github.mcp

import io.jaiclaw.core.mcp.McpToolResult
import io.jaiclaw.core.tool.ToolCallback
import io.jaiclaw.core.tool.ToolDefinition
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

class GithubToolsMcpProviderSpec extends Specification {

    def "server name is 'github'"() {
        given:
        def provider = new GithubToolsMcpProvider([])

        expect:
        provider.getServerName() == "github"
        provider.getServerDescription().contains("GitHub tools")
    }

    def "getTools reflects the constructor-supplied tool list"() {
        given:
        def toolA = tool("tool_a", "Tool A")
        def toolB = tool("tool_b", "Tool B")
        def provider = new GithubToolsMcpProvider([toolA, toolB])

        when:
        def defs = provider.getTools()

        then:
        defs.size() == 2
        defs.collect { it.name() }.sort() == ["tool_a", "tool_b"]
    }

    def "execute routes to the matching tool and returns success"() {
        given:
        def calledArgs = null
        def toolA = new ToolCallback() {
            ToolDefinition definition() {
                new ToolDefinition("tool_a", "desc", "GitHub", '{"type":"object"}')
            }
            ToolResult execute(Map<String, Object> params, io.jaiclaw.core.tool.ToolContext context) {
                calledArgs = params
                new ToolResult.Success("output for " + params.get("k"))
            }
        }
        def provider = new GithubToolsMcpProvider([toolA])

        when:
        McpToolResult result = provider.execute("tool_a", [k: "v"], null)

        then:
        !result.isError()
        result.content() == "output for v"
        calledArgs == [k: "v"]
    }

    def "execute returns error result for unknown tool"() {
        given:
        def provider = new GithubToolsMcpProvider([])

        when:
        def result = provider.execute("no_such_tool", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }

    def "execute propagates ToolResult.Error as isError:true"() {
        given:
        def toolA = new ToolCallback() {
            ToolDefinition definition() {
                new ToolDefinition("tool_a", "desc", "GitHub", '{"type":"object"}')
            }
            ToolResult execute(Map<String, Object> params, io.jaiclaw.core.tool.ToolContext context) {
                new ToolResult.Error("something failed")
            }
        }
        def provider = new GithubToolsMcpProvider([toolA])

        when:
        def result = provider.execute("tool_a", [:], null)

        then:
        result.isError()
        result.content() == "something failed"
    }

    private ToolCallback tool(String name, String description) {
        return new ToolCallback() {
            ToolDefinition definition() {
                new ToolDefinition(name, description, "GitHub", '{"type":"object","properties":{}}')
            }
            ToolResult execute(Map<String, Object> params, io.jaiclaw.core.tool.ToolContext context) {
                new ToolResult.Success("ok")
            }
        }
    }
}
