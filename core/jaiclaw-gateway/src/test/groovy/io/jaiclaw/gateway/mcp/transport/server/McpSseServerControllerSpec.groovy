package io.jaiclaw.gateway.mcp.transport.server

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.jaiclaw.core.mcp.McpToolDefinition
import io.jaiclaw.core.mcp.McpToolProvider
import io.jaiclaw.core.mcp.McpToolResult
import io.jaiclaw.gateway.mcp.McpServerRegistry
import spock.lang.Specification

class McpSseServerControllerSpec extends Specification {

    ObjectMapper objectMapper = new ObjectMapper()
    McpServerRegistry registry = new McpServerRegistry()
    McpSseServerController controller

    def setup() {
        controller = new McpSseServerController(registry, objectMapper)
    }

    def "SSE endpoint sends endpoint event for known server"() {
        given:
        registry.register(mockProvider("test-server"))

        when:
        def emitter = controller.sseConnect("test-server")

        then:
        emitter != null
    }

    def "SSE endpoint throws for unknown server"() {
        when:
        controller.sseConnect("nonexistent")

        then:
        thrown(IllegalArgumentException)
    }

    def "JSON-RPC initialize returns protocol version and server info"() {
        given:
        registry.register(mockProvider("test-server"))
        def request = objectMapper.readTree('''
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
        ''')

        when:
        def response = controller.jsonRpc("test-server", request, [:])

        then:
        response.statusCode.value() == 200
        def body = response.body
        body.get("result").get("protocolVersion").asText() == "2024-11-05"
        body.get("result").get("serverInfo").get("name").asText() == "test-server"
        body.get("result").has("capabilities")
    }

    def "JSON-RPC tools/list returns tool definitions"() {
        given:
        registry.register(mockProvider("test-server"))
        def request = objectMapper.readTree('''
            {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
        ''')

        when:
        def response = controller.jsonRpc("test-server", request, [:])

        then:
        response.statusCode.value() == 200
        def tools = response.body.get("result").get("tools")
        tools.isArray()
        tools.size() == 2
        tools.get(0).get("name").asText() == "tool_one"
        tools.get(1).get("name").asText() == "tool_two"
    }

    def "JSON-RPC tools/call dispatches to provider"() {
        given:
        def provider = mockProvider("test-server")
        registry.register(provider)
        def request = objectMapper.readTree('''
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"tool_one","arguments":{"key":"value"}}}
        ''')

        when:
        def response = controller.jsonRpc("test-server", request, [:])

        then:
        response.statusCode.value() == 200
        def result = response.body.get("result")
        result.get("isError").asBoolean() == false
        result.get("content").get(0).get("text").asText() == '{"result":"ok"}'
    }

    def "JSON-RPC unknown method returns error"() {
        given:
        registry.register(mockProvider("test-server"))
        def request = objectMapper.readTree('''
            {"jsonrpc":"2.0","id":4,"method":"unknown/method","params":{}}
        ''')

        when:
        def response = controller.jsonRpc("test-server", request, [:])

        then:
        response.statusCode.value() == 200
        def body = response.body
        body.has("error")
        body.get("error").get("code").asInt() == -32601
    }

    def "JSON-RPC notification (no id) returns 204"() {
        given:
        registry.register(mockProvider("test-server"))
        def request = objectMapper.readTree('''
            {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
        ''')

        when:
        def response = controller.jsonRpc("test-server", request, [:])

        then:
        response.statusCode.value() == 204
    }

    def "JSON-RPC for unknown server returns 404"() {
        given:
        def request = objectMapper.readTree('''
            {"jsonrpc":"2.0","id":5,"method":"initialize","params":{}}
        ''')

        when:
        def response = controller.jsonRpc("nonexistent", request, [:])

        then:
        response.statusCode.value() == 404
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
}
