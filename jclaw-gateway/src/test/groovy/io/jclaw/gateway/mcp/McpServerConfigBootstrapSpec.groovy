package io.jclaw.gateway.mcp

import io.jclaw.config.*
import spock.lang.Specification

class McpServerConfigBootstrapSpec extends Specification {

    def "does nothing when no MCP servers configured"() {
        given:
        def properties = new JClawProperties(null, null, null, null, null, null, null, null, null)
        def registry = new McpServerRegistry()
        def bootstrap = new McpServerConfigBootstrap(properties, registry)

        when:
        bootstrap.afterSingletonsInstantiated()

        then:
        registry.size() == 0
    }

    def "does nothing when MCP servers map is empty"() {
        given:
        def mcpServers = new McpServerProperties([:])
        def properties = new JClawProperties(null, null, null, null, null, null, null, null, mcpServers)
        def registry = new McpServerRegistry()
        def bootstrap = new McpServerConfigBootstrap(properties, registry)

        when:
        bootstrap.afterSingletonsInstantiated()

        then:
        registry.size() == 0
    }

    def "logs warning and continues when MCP server connection fails"() {
        given:
        def entry = new McpServerProperties.McpServerEntry(
                "Test server", "http", null, null, "http://localhost:19999", null)
        def mcpServers = new McpServerProperties(["test-server": entry])
        def properties = new JClawProperties(null, null, null, null, null, null, null, null, mcpServers)
        def registry = new McpServerRegistry()
        def bootstrap = new McpServerConfigBootstrap(properties, registry)

        when:
        bootstrap.afterSingletonsInstantiated()

        then:
        // Should not throw — logs warning instead
        noExceptionThrown()
        // Server not registered due to connection failure
        registry.size() == 0
    }

    def "logs warning for stdio server with missing command"() {
        given:
        def entry = new McpServerProperties.McpServerEntry(
                "Bad server", "stdio", null, null, null, null)
        def mcpServers = new McpServerProperties(["bad": entry])
        def properties = new JClawProperties(null, null, null, null, null, null, null, null, mcpServers)
        def registry = new McpServerRegistry()
        def bootstrap = new McpServerConfigBootstrap(properties, registry)

        when:
        bootstrap.afterSingletonsInstantiated()

        then:
        noExceptionThrown()
        registry.size() == 0
    }
}
