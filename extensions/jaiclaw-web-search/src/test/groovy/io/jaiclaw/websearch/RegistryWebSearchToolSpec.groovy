package io.jaiclaw.websearch

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

class RegistryWebSearchToolSpec extends Specification {

    def registry = new WebSearchRegistry()
    def ctx = new ToolContext("agent1", "session1", "sid1", "/tmp")

    def "tool name is web_search"() {
        given:
        def tool = new RegistryWebSearchTool(registry, 5)

        expect:
        tool.definition().name() == "web_search"
    }

    def "execute delegates to active provider"() {
        given:
        def provider = Stub(WebSearchProvider) {
            id() >> "mock"
            isConfigured() >> true
            search("test query", 5) >> [
                new WebSearchResult("Result 1", "https://example.com", "A snippet")
            ]
        }
        registry.register(provider)
        def tool = new RegistryWebSearchTool(registry, 5)

        when:
        def result = tool.execute([query: "test query"], ctx)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains("Result 1")
        ((ToolResult.Success) result).content().contains("https://example.com")
    }

    def "execute returns error when no provider configured"() {
        given:
        def tool = new RegistryWebSearchTool(registry, 5)

        when:
        def result = tool.execute([query: "test"], ctx)

        then:
        result instanceof ToolResult.Error
    }
}
