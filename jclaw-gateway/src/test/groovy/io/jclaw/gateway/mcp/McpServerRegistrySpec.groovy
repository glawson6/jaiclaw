package io.jclaw.gateway.mcp

import io.jclaw.core.mcp.McpToolDefinition
import io.jclaw.core.mcp.McpToolProvider
import io.jclaw.core.mcp.McpToolResult
import io.jclaw.core.tenant.TenantContext
import spock.lang.Specification

class McpServerRegistrySpec extends Specification {

    def "register and retrieve provider"() {
        given:
        def registry = new McpServerRegistry()
        def provider = createProvider("resources", "Resource server", 2)

        when:
        registry.register(provider)

        then:
        registry.get("resources").isPresent()
        registry.get("resources").get().getServerName() == "resources"
        registry.size() == 1
        registry.serverNames() == ["resources"]
    }

    def "get returns empty for unknown server"() {
        given:
        def registry = new McpServerRegistry()

        expect:
        registry.get("unknown").isEmpty()
    }

    def "constructor accepts list of providers"() {
        given:
        def p1 = createProvider("server1", "Server 1", 1)
        def p2 = createProvider("server2", "Server 2", 3)

        when:
        def registry = new McpServerRegistry([p1, p2])

        then:
        registry.size() == 2
        registry.serverNames().containsAll(["server1", "server2"])
    }

    private McpToolProvider createProvider(String name, String desc, int toolCount) {
        def tools = (1..toolCount).collect { new McpToolDefinition("tool$it", "Tool $it", "{}") }
        return new McpToolProvider() {
            String getServerName() { name }
            String getServerDescription() { desc }
            List<McpToolDefinition> getTools() { tools }
            McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
                McpToolResult.success("executed $toolName")
            }
        }
    }
}
