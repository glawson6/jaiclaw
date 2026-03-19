package io.jclaw.gateway.mcp.transport

import io.jclaw.core.mcp.McpToolDefinition
import spock.lang.Specification

class HttpMcpToolProviderSpec extends Specification {

    def "returns configured server name"() {
        given:
        def provider = new HttpMcpToolProvider("test-server", "Test server", "http://localhost:3000", null)

        expect:
        provider.getServerName() == "test-server"
    }

    def "returns configured description"() {
        given:
        def provider = new HttpMcpToolProvider("test-server", "My test server", "http://localhost:3000", null)

        expect:
        provider.getServerDescription() == "My test server"
    }

    def "uses server name as description when description is null"() {
        given:
        def provider = new HttpMcpToolProvider("test-server", null, "http://localhost:3000", null)

        expect:
        provider.getServerDescription() == "test-server"
    }

    def "returns empty tool list before initialization"() {
        given:
        def provider = new HttpMcpToolProvider("test-server", "Test", "http://localhost:3000", null)

        expect:
        provider.getTools().isEmpty()
    }

    def "execute returns error when server is unreachable"() {
        given:
        def provider = new HttpMcpToolProvider("test-server", "Test", "http://localhost:19999", null)

        when:
        def result = provider.execute("some-tool", [:], null)

        then:
        result.isError()
        result.content().contains("Tool execution failed")
    }
}
