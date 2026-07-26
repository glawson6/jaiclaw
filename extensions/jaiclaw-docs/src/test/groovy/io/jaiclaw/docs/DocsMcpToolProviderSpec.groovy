package io.jaiclaw.docs

import tools.jackson.databind.ObjectMapper
import spock.lang.Specification

class DocsMcpToolProviderSpec extends Specification {

    DocsRepository repository
    DocsMcpToolProvider provider

    def setup() {
        repository = new DocsRepository([
            new DocsEntry("docs://arch", "architecture", "text/markdown",
                "# Architecture\nMulti-tenancy and virtual threads", ["architecture"]),
            new DocsEntry("docs://ops", "operations", "text/markdown",
                "# Operations\nDeployment and monitoring guide", ["operations"])
        ])
        provider = new DocsMcpToolProvider(repository, new ObjectMapper())
    }

    def "server name is docs"() {
        expect:
        provider.getServerName() == "docs"
    }

    def "getTools returns search_docs tool"() {
        when:
        def tools = provider.getTools()

        then:
        tools.size() == 1
        tools[0].name() == "search_docs"
        tools[0].inputSchema().contains('"query"')
    }

    def "search_docs returns results for matching query"() {
        when:
        def result = provider.execute("search_docs", [query: "multi-tenancy"], null)

        then:
        !result.isError()
        result.content().contains("docs://arch")
        result.content().contains("resultCount")
    }

    def "search_docs returns error for missing query"() {
        when:
        def result = provider.execute("search_docs", [:], null)

        then:
        result.isError()
        result.content().contains("Missing required parameter: query")
    }

    def "search_docs respects maxResults parameter"() {
        when:
        def result = provider.execute("search_docs", [query: "guide", maxResults: 1], null)

        then:
        !result.isError()
        def parsed = new ObjectMapper().readTree(result.content())
        parsed.get("resultCount").asInt() <= 1
    }

    def "execute returns error for unknown tool"() {
        when:
        def result = provider.execute("unknown_tool", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool: unknown_tool")
    }
}
