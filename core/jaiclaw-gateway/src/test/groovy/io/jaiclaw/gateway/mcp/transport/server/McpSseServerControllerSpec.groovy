package io.jaiclaw.gateway.mcp.transport.server

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import io.jaiclaw.core.mcp.McpResourceContent
import io.jaiclaw.core.mcp.McpResourceDefinition
import io.jaiclaw.core.mcp.McpResourceProvider
import io.jaiclaw.core.mcp.McpToolDefinition
import io.jaiclaw.core.mcp.McpToolProvider
import io.jaiclaw.core.mcp.McpToolResult
import io.jaiclaw.core.tenant.TenantContext
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
        registry.register(mockToolProvider("test-server"))

        when:
        def emitter = controller.sseConnect("test-server")

        then:
        emitter != null
    }

    def "SSE endpoint works for resource-only server"() {
        given:
        registry.registerResource(mockResourceProvider("res-server"))

        when:
        def emitter = controller.sseConnect("res-server")

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
        registry.register(mockToolProvider("test-server"))
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

    def "initialize declares tools capability for tool-only server"() {
        given:
        registry.register(mockToolProvider("tool-server"))
        def request = objectMapper.readTree('''
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
        ''')

        when:
        def response = controller.jsonRpc("tool-server", request, [:])

        then:
        def caps = response.body.get("result").get("capabilities")
        caps.has("tools")
        !caps.has("resources")
    }

    def "initialize declares resources capability for resource-only server"() {
        given:
        registry.registerResource(mockResourceProvider("res-server"))
        def request = objectMapper.readTree('''
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
        ''')

        when:
        def response = controller.jsonRpc("res-server", request, [:])

        then:
        def caps = response.body.get("result").get("capabilities")
        !caps.has("tools")
        caps.has("resources")
    }

    def "initialize declares both capabilities for server with tools and resources"() {
        given:
        registry.register(mockToolProvider("both-server"))
        registry.registerResource(mockResourceProvider("both-server"))
        def request = objectMapper.readTree('''
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
        ''')

        when:
        def response = controller.jsonRpc("both-server", request, [:])

        then:
        def caps = response.body.get("result").get("capabilities")
        caps.has("tools")
        caps.has("resources")
    }

    def "JSON-RPC tools/list returns tool definitions"() {
        given:
        registry.register(mockToolProvider("test-server"))
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
        def provider = mockToolProvider("test-server")
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

    def "JSON-RPC resources/list returns resource definitions"() {
        given:
        registry.registerResource(mockResourceProvider("docs"))
        def request = objectMapper.readTree('''
            {"jsonrpc":"2.0","id":10,"method":"resources/list","params":{}}
        ''')

        when:
        def response = controller.jsonRpc("docs", request, [:])

        then:
        response.statusCode.value() == 200
        def resources = response.body.get("result").get("resources")
        resources.isArray()
        resources.size() == 2
        resources.get(0).get("uri").asText() == "docs://architecture"
        resources.get(0).get("name").asText() == "Architecture"
        resources.get(0).get("mimeType").asText() == "text/markdown"
    }

    def "JSON-RPC resources/read returns content for known URI"() {
        given:
        registry.registerResource(mockResourceProvider("docs"))
        def request = objectMapper.readTree('''
            {"jsonrpc":"2.0","id":11,"method":"resources/read","params":{"uri":"docs://architecture"}}
        ''')

        when:
        def response = controller.jsonRpc("docs", request, [:])

        then:
        response.statusCode.value() == 200
        def contents = response.body.get("result").get("contents")
        contents.isArray()
        contents.size() == 1
        contents.get(0).get("uri").asText() == "docs://architecture"
        contents.get(0).get("text").asText() == "# Architecture\nContent here"
    }

    def "JSON-RPC resources/read returns error for unknown URI"() {
        given:
        registry.registerResource(mockResourceProvider("docs"))
        def request = objectMapper.readTree('''
            {"jsonrpc":"2.0","id":12,"method":"resources/read","params":{"uri":"docs://nonexistent"}}
        ''')

        when:
        def response = controller.jsonRpc("docs", request, [:])

        then:
        response.statusCode.value() == 200
        response.body.has("error")
        response.body.get("error").get("code").asInt() == -32603
    }

    def "JSON-RPC unknown method returns error"() {
        given:
        registry.register(mockToolProvider("test-server"))
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
        registry.register(mockToolProvider("test-server"))
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

    private McpToolProvider mockToolProvider(String name) {
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

    private McpResourceProvider mockResourceProvider(String name) {
        def provider = Mock(McpResourceProvider)
        provider.getServerName() >> name
        provider.getServerDescription() >> "Test resource server"
        provider.getResources() >> [
            new McpResourceDefinition("docs://architecture", "Architecture", "text/markdown", "Architecture docs"),
            new McpResourceDefinition("docs://operations", "Operations", "text/markdown", "Operations docs")
        ]
        provider.read("docs://architecture", _) >> Optional.of(
            new McpResourceContent("docs://architecture", "text/markdown", "# Architecture\nContent here")
        )
        provider.read("docs://operations", _) >> Optional.of(
            new McpResourceContent("docs://operations", "text/markdown", "# Operations\nContent here")
        )
        provider.read("docs://nonexistent", _) >> Optional.empty()
        return provider
    }
}
