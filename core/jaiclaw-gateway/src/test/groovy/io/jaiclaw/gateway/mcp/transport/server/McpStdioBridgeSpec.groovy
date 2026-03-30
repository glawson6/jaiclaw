package io.jaiclaw.gateway.mcp.transport.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.jaiclaw.core.mcp.McpToolDefinition
import io.jaiclaw.core.mcp.McpToolProvider
import io.jaiclaw.core.mcp.McpToolResult
import spock.lang.Specification

class McpStdioBridgeSpec extends Specification {

    ObjectMapper objectMapper = new ObjectMapper()

    def "initialize handshake returns protocol version and server info"() {
        given:
        def provider = mockProvider("test-server")
        def (bridge, output) = createBridge(provider, [
            '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
        ])

        when:
        bridge.run()

        then:
        def responses = parseResponses(output)
        responses.size() == 1
        responses[0].get("result").get("protocolVersion").asText() == "2024-11-05"
        responses[0].get("result").get("serverInfo").get("name").asText() == "test-server"
    }

    def "notifications are silently ignored"() {
        given:
        def provider = mockProvider("test-server")
        def (bridge, output) = createBridge(provider, [
            '{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}',
            '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
        ])

        when:
        bridge.run()

        then:
        def responses = parseResponses(output)
        responses.size() == 1 // only the initialize response, not the notification
    }

    def "tools/list returns tool definitions"() {
        given:
        def provider = mockProvider("test-server")
        def (bridge, output) = createBridge(provider, [
            '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
        ])

        when:
        bridge.run()

        then:
        def responses = parseResponses(output)
        responses.size() == 1
        def tools = responses[0].get("result").get("tools")
        tools.size() == 2
        tools.get(0).get("name").asText() == "tool_one"
        tools.get(1).get("name").asText() == "tool_two"
    }

    def "tools/call dispatches to provider and returns result"() {
        given:
        def provider = mockProvider("test-server")
        def (bridge, output) = createBridge(provider, [
            '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"tool_one","arguments":{"key":"value"}}}'
        ])

        when:
        bridge.run()

        then:
        def responses = parseResponses(output)
        responses.size() == 1
        def content = responses[0].get("result").get("content")
        content.get(0).get("text").asText() == '{"result":"ok"}'
        responses[0].get("result").get("isError").asBoolean() == false
    }

    def "tools/call with error result sets isError flag"() {
        given:
        def provider = Mock(McpToolProvider)
        provider.getServerName() >> "test-server"
        provider.getServerDescription() >> "Test"
        provider.getTools() >> [new McpToolDefinition("fail_tool", "Fails", '{}')]
        provider.execute("fail_tool", _, _) >> McpToolResult.error("Something broke")
        def (bridge, output) = createBridge(provider, [
            '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"fail_tool","arguments":{}}}'
        ])

        when:
        bridge.run()

        then:
        def responses = parseResponses(output)
        responses[0].get("result").get("isError").asBoolean() == true
        responses[0].get("result").get("content").get(0).get("text").asText() == "Something broke"
    }

    def "unknown method returns JSON-RPC error"() {
        given:
        def provider = mockProvider("test-server")
        def (bridge, output) = createBridge(provider, [
            '{"jsonrpc":"2.0","id":1,"method":"bogus/method","params":{}}'
        ])

        when:
        bridge.run()

        then:
        def responses = parseResponses(output)
        responses.size() == 1
        responses[0].has("error")
        responses[0].get("error").get("code").asInt() == -32601
        responses[0].get("error").get("message").asText().contains("Unknown method")
    }

    def "full handshake sequence works"() {
        given:
        def provider = mockProvider("test-server")
        def (bridge, output) = createBridge(provider, [
            '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}',
            '{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}',
            '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}',
            '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"tool_one","arguments":{"x":"1"}}}'
        ])

        when:
        bridge.run()

        then:
        def responses = parseResponses(output)
        responses.size() == 3 // initialize, tools/list, tools/call (notification has no response)
        responses[0].get("id").asInt() == 1
        responses[1].get("id").asInt() == 2
        responses[2].get("id").asInt() == 3
    }

    // ── helpers ──

    private McpToolProvider mockProvider(String name) {
        def provider = Mock(McpToolProvider)
        provider.getServerName() >> name
        provider.getServerDescription() >> "Test server"
        provider.getTools() >> [
            new McpToolDefinition("tool_one", "First tool", '{"type":"object","properties":{"key":{"type":"string"}}}'),
            new McpToolDefinition("tool_two", "Second tool", '{"type":"object","properties":{}}')
        ]
        provider.execute("tool_one", _, _) >> McpToolResult.success('{"result":"ok"}')
        provider.execute("tool_two", _, _) >> McpToolResult.success('{"result":"ok"}')
        return provider
    }

    private List createBridge(McpToolProvider provider, List<String> inputLines) {
        def input = new ByteArrayInputStream((inputLines.join("\n") + "\n").getBytes("UTF-8"))
        def output = new ByteArrayOutputStream()
        def bridge = new McpStdioBridge(provider, objectMapper, input, output)
        return [bridge, output]
    }

    private List parseResponses(ByteArrayOutputStream output) {
        output.toString("UTF-8").trim().split("\n")
            .findAll { !it.isBlank() }
            .collect { objectMapper.readTree(it) }
    }
}
