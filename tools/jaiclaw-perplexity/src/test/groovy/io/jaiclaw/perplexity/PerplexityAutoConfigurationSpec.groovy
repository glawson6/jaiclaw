package io.jaiclaw.perplexity

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.tools.ToolRegistry
import spock.lang.Specification

class PerplexityAutoConfigurationSpec extends Specification {

    def "registers three tools into ToolRegistry"() {
        given:
        def registry = new ToolRegistry()
        def config = new PerplexityAutoConfiguration()

        when:
        config.perplexityToolsRegistrar(registry)

        then:
        registry.size() == 3
        registry.contains("perplexity_search")
        registry.contains("perplexity_web_search")
        registry.contains("perplexity_research")
    }

    def "all tools are in perplexity section"() {
        given:
        def registry = new ToolRegistry()
        def config = new PerplexityAutoConfiguration()
        config.perplexityToolsRegistrar(registry)

        when:
        def tools = registry.resolveBySection("perplexity")

        then:
        tools.size() == 3
    }

    def "all tools are available in FULL profile"() {
        given:
        def registry = new ToolRegistry()
        def config = new PerplexityAutoConfiguration()
        config.perplexityToolsRegistrar(registry)

        when:
        def tools = registry.resolveForProfile(ToolProfile.FULL)

        then:
        tools.size() == 3
    }

    def "perplexity_search tool has correct definition"() {
        given:
        def registry = new ToolRegistry()
        def config = new PerplexityAutoConfiguration()
        config.perplexityToolsRegistrar(registry)

        when:
        def tool = registry.resolve("perplexity_search").get()
        def def_ = tool.definition()

        then:
        def_.name() == "perplexity_search"
        def_.section() == "perplexity"
        def_.description().contains("Search the web")
        def_.inputSchema().contains("query")
        def_.profiles() == [ToolProfile.FULL] as Set
    }

    def "perplexity_web_search tool has correct definition"() {
        given:
        def registry = new ToolRegistry()
        def config = new PerplexityAutoConfiguration()
        config.perplexityToolsRegistrar(registry)

        when:
        def tool = registry.resolve("perplexity_web_search").get()
        def def_ = tool.definition()

        then:
        def_.name() == "perplexity_web_search"
        def_.section() == "perplexity"
        def_.description().contains("Raw web search")
        def_.inputSchema().contains("query")
    }

    def "perplexity_research tool has correct definition"() {
        given:
        def registry = new ToolRegistry()
        def config = new PerplexityAutoConfiguration()
        config.perplexityToolsRegistrar(registry)

        when:
        def tool = registry.resolve("perplexity_research").get()
        def def_ = tool.definition()

        then:
        def_.name() == "perplexity_research"
        def_.section() == "perplexity"
        def_.description().contains("Deep multi-step research")
        def_.inputSchema().contains("query")
    }

    def "perplexity_search returns error when API key is not set"() {
        given:
        def registry = new ToolRegistry()
        def config = new PerplexityAutoConfiguration()
        config.perplexityToolsRegistrar(registry)
        def tool = registry.resolve("perplexity_search").get()
        def context = new ToolContext("agent", "session", "sid", "/tmp")

        when:
        def result = tool.execute([query: "test"], context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("PERPLEXITY_API_KEY")
    }

    def "perplexity_web_search returns error when API key is not set"() {
        given:
        def registry = new ToolRegistry()
        def config = new PerplexityAutoConfiguration()
        config.perplexityToolsRegistrar(registry)
        def tool = registry.resolve("perplexity_web_search").get()
        def context = new ToolContext("agent", "session", "sid", "/tmp")

        when:
        def result = tool.execute([query: "test"], context)

        then:
        result instanceof ToolResult.Error
    }

    def "perplexity_research returns error when API key is not set"() {
        given:
        def registry = new ToolRegistry()
        def config = new PerplexityAutoConfiguration()
        config.perplexityToolsRegistrar(registry)
        def tool = registry.resolve("perplexity_research").get()
        def context = new ToolContext("agent", "session", "sid", "/tmp")

        when:
        def result = tool.execute([query: "test"], context)

        then:
        result instanceof ToolResult.Error
    }

    def "tools are not available in MINIMAL profile"() {
        given:
        def registry = new ToolRegistry()
        def config = new PerplexityAutoConfiguration()
        config.perplexityToolsRegistrar(registry)

        when:
        def tools = registry.resolveForProfile(ToolProfile.MINIMAL)

        then:
        tools.isEmpty()
    }
}
