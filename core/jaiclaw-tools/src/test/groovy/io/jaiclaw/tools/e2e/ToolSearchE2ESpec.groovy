package io.jaiclaw.tools.e2e

import io.jaiclaw.core.tool.ToolCallback
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolDefinition
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolProfileHolder
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.tools.ToolRegistry
import io.jaiclaw.tools.builtin.ToolSearchTool
import io.jaiclaw.tools.search.SessionToolDiscoveries
import spock.lang.Specification

/**
 * §5.2 row 3 of the 1.2.0 plan: a 60-tool catalog where only the core few plus
 * tool_search reach the model, and a deferred tool becomes callable after
 * discovery.
 */
class ToolSearchE2ESpec extends Specification {

    static final String SESSION = "agent:slack:acme:C1"

    ToolRegistry registry = new ToolRegistry()
    SessionToolDiscoveries discoveries = new SessionToolDiscoveries()

    private static ToolCallback tool(String name, String section, String source,
                                     String description = null, Set<String> keywords = [] as Set) {
        def def_ = ToolDefinition.builder()
                .name(name)
                .description(description ?: "performs $name")
                .section(section)
                .inputSchema('{"type":"object"}')
                .profiles([ToolProfile.FULL] as Set)
                .source(source)
                .keywords(keywords)
                .build()
        new ToolCallback() {
            ToolDefinition definition() { def_ }
            ToolResult execute(Map<String, Object> p, ToolContext c) { new ToolResult.Success("ran $name") }
        }
    }

    private ToolContext ctx() {
        new ToolContext("agent", SESSION, "s1", ".")
    }

    def setup() {
        // 6 core builtins the model should always see.
        ["file_read", "file_write", "shell_exec", "web_fetch", "web_search", "ascii_box"].each {
            registry.register(tool(it, "core", ToolDefinition.SOURCE_BUILTIN))
        }
        // 54 noisy tools from MCP + Camel that should be deferred.
        (1..27).each { registry.register(tool("mcp_tool_$it", "remote", ToolDefinition.SOURCE_MCP)) }
        (1..27).each { registry.register(tool("camel_tool_$it", "routes", ToolDefinition.SOURCE_CAMEL)) }
    }

    /**
     * Mirrors what JaiClawToolsAutoConfiguration does when tool search is enabled:
     * register tool_search, then defer everything matching the rule EXCEPT
     * tool_search itself. Expressed as a predicate rather than via
     * ToolSearchProperties because jaiclaw-tools must not depend on
     * jaiclaw-config.
     */
    private ToolSearchTool enableSearch(Closure<Boolean> deferRule = { false }, int limit = 5) {
        def searchTool = new ToolSearchTool(registry, discoveries, limit)
        registry.register(searchTool)
        registry.markDeferred { def_ ->
            !"tool_search".equals(def_.name()) && deferRule(def_)
        }
        searchTool
    }

    private static Closure<Boolean> bySource(String... sources) {
        def wanted = sources as Set
        return { def_ -> wanted.contains(def_.source()) }
    }

    def "60 tools registered, only 6 core plus tool_search reach the model"() {
        when:
        enableSearch(bySource(ToolDefinition.SOURCE_MCP, ToolDefinition.SOURCE_CAMEL))

        then: "the catalog is 61 (60 + tool_search)"
        registry.size() == 61

        and: "but only 7 schemas are sent on turn 1"
        def active = registry.resolveActive(ToolProfile.FULL, discoveries.discovered(SESSION))
        active.size() == 7
        active*.definition()*.name().toSorted() == [
                "ascii_box", "file_read", "file_write", "shell_exec",
                "tool_search", "web_fetch", "web_search"]
    }

    def "searching surfaces a deferred tool and makes it callable next turn"() {
        given:
        def searchTool = enableSearch(bySource(ToolDefinition.SOURCE_MCP, ToolDefinition.SOURCE_CAMEL))
        registry.register(tool("mcp_tool_jira", "remote", ToolDefinition.SOURCE_MCP,
                "Create and update Jira issues", ["jira", "ticket"] as Set))
        registry.markDeferred { it.source() == ToolDefinition.SOURCE_MCP }

        expect: "not visible up front"
        !registry.resolveActive(ToolProfile.FULL, discoveries.discovered(SESSION))*.definition()*.name()
                .contains("mcp_tool_jira")

        when: "the model searches for it"
        ToolProfileHolder.set(ToolProfile.FULL)
        def result = searchTool.execute([query: "jira ticket"], ctx())

        then: "the schema comes back"
        result instanceof ToolResult.Success
        result.content().contains("mcp_tool_jira")
        result.content().contains("input_schema")

        and: "and it is admitted for the rest of the session"
        registry.resolveActive(ToolProfile.FULL, discoveries.discovered(SESSION))*.definition()*.name()
                .contains("mcp_tool_jira")

        cleanup:
        ToolProfileHolder.clear()
    }

    def "a discovered tool stays available on later turns"() {
        given:
        def searchTool = enableSearch(bySource(ToolDefinition.SOURCE_MCP))
        ToolProfileHolder.set(ToolProfile.FULL)

        when:
        searchTool.execute([query: "mcp_tool_3"], ctx())

        then: "turn 2, turn 3 ... it is still there — otherwise the model would re-search every turn"
        3.times {
            assert registry.resolveActive(ToolProfile.FULL, discoveries.discovered(SESSION))
                    *.definition()*.name().contains("mcp_tool_3")
        }

        cleanup:
        ToolProfileHolder.clear()
    }

    def "discoveries do not leak between sessions"() {
        given:
        def searchTool = enableSearch(bySource(ToolDefinition.SOURCE_MCP))
        ToolProfileHolder.set(ToolProfile.FULL)

        when: "session A discovers a tool"
        searchTool.execute([query: "mcp_tool_5"], new ToolContext("agent", "session-A", "s", "."))

        then: "session B still cannot see it"
        !registry.resolveActive(ToolProfile.FULL, discoveries.discovered("session-B"))
                *.definition()*.name().contains("mcp_tool_5")

        cleanup:
        ToolProfileHolder.clear()
    }

    def "with search disabled the model sees every tool, exactly as before 1.2.0"() {
        given: "no deferral rules applied at all"
        def before = registry.resolveForProfile(ToolProfile.FULL)*.definition()*.name().toSorted()

        expect:
        registry.resolveActive(ToolProfile.FULL, null)*.definition()*.name().toSorted() == before
        before.size() == 60
    }

    def "search results never exceed the run's tool profile"() {
        given: "a deferred tool only CODING may use"
        registry.register(tool("privileged_exec", "exec", ToolDefinition.SOURCE_BUILTIN))
        def restricted = ToolDefinition.builder()
                .name("coding_only").description("dangerous").section("exec")
                .inputSchema('{}').profiles([ToolProfile.CODING] as Set).build()
        registry.register(new ToolCallback() {
            ToolDefinition definition() { restricted }
            ToolResult execute(Map<String, Object> p, ToolContext c) { new ToolResult.Success("x") }
        })
        def searchTool = enableSearch()

        when: "a MINIMAL run searches for it"
        ToolProfileHolder.set(ToolProfile.MINIMAL)
        def result = searchTool.execute([query: "coding_only"], ctx())

        then: "search cannot be used to enumerate tools the run may not call"
        // Assert on returned tool NAMES, not the raw payload — the query string
        // itself is echoed back, so a substring check would match its own input.
        def names = (result.content() =~ /"name":"([^"]+)"/).collect { it[1] }
        !names.contains("coding_only")

        cleanup:
        ToolProfileHolder.clear()
    }

    def "an unhelpful query returns a usable note rather than an empty payload"() {
        given:
        def searchTool = enableSearch()
        ToolProfileHolder.set(ToolProfile.FULL)

        when:
        def result = searchTool.execute([query: "quantum entanglement telemetry"], ctx())

        then:
        result instanceof ToolResult.Success
        result.content().contains('"count":0')
        result.content().contains("proceed without one")

        cleanup:
        ToolProfileHolder.clear()
    }

    def "the search limit is honoured and capped"() {
        given:
        def searchTool = enableSearch()
        ToolProfileHolder.set(ToolProfile.FULL)

        when: "asking for 3"
        def three = searchTool.execute([query: "mcp_tool", limit: 3], ctx())

        then:
        (three.content() =~ /"name":/).count == 3

        when: "asking for an absurd number"
        def capped = searchTool.execute([query: "tool", limit: 9999], ctx())

        then: "one search cannot undo the context saving deferral bought"
        (capped.content() =~ /"name":/).count <= 25

        cleanup:
        ToolProfileHolder.clear()
    }

    def "tool_search itself is never deferred"() {
        given: "a rule broad enough to match everything"

        when:
        enableSearch({ true })

        then: "the model must always retain the means of discovery"
        !registry.isDeferred("tool_search")
        registry.resolveActive(ToolProfile.FULL, null)*.definition()*.name() == ["tool_search"]
    }
}
