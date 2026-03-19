package io.jclaw.gateway.mcp.transport

import spock.lang.Specification

class StdioMcpToolProviderSpec extends Specification {

    def "returns configured server name"() {
        given:
        def provider = new StdioMcpToolProvider("fs-server", "Filesystem", "node", ["server.js"])

        expect:
        provider.getServerName() == "fs-server"
    }

    def "returns configured description"() {
        given:
        def provider = new StdioMcpToolProvider("fs-server", "Filesystem access", "node", [])

        expect:
        provider.getServerDescription() == "Filesystem access"
    }

    def "uses server name as description when description is null"() {
        given:
        def provider = new StdioMcpToolProvider("fs-server", null, "node", [])

        expect:
        provider.getServerDescription() == "fs-server"
    }

    def "returns empty tool list before start"() {
        given:
        def provider = new StdioMcpToolProvider("test", "Test", "echo", [])

        expect:
        provider.getTools().isEmpty()
    }

    def "destroy handles null process gracefully"() {
        given:
        def provider = new StdioMcpToolProvider("test", "Test", "echo", [])

        when:
        provider.destroy()

        then:
        noExceptionThrown()
    }

    def "execute returns error when process not started"() {
        given:
        def provider = new StdioMcpToolProvider("test", "Test", "echo", [])

        when:
        def result = provider.execute("tool", [:], null)

        then:
        result.isError()
        result.content().contains("Tool execution failed")
    }
}
