package io.jaiclaw.tools

import io.jaiclaw.core.tool.ToolCallback
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolDefinition
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

class ToolRegistryDeferralSpec extends Specification {

    private static ToolCallback tool(String name, String section = "misc",
                                     String source = ToolDefinition.SOURCE_BUILTIN,
                                     Set<ToolProfile> profiles = [ToolProfile.FULL] as Set) {
        def def_ = ToolDefinition.builder()
                .name(name).description("does $name").section(section)
                .inputSchema('{"type":"object"}').profiles(profiles).source(source).build()
        new ToolCallback() {
            ToolDefinition definition() { def_ }
            ToolResult execute(Map<String, Object> p, ToolContext c) { new ToolResult.Success("ok") }
        }
    }

    def "with nothing deferred, resolveActive is identical to resolveForProfile"() {
        given: "the pre-1.2.0 baseline — the feature must be inert until switched on"
        def registry = new ToolRegistry()
        registry.registerAll([tool("a"), tool("b"), tool("c")])

        expect:
        registry.resolveActive(ToolProfile.FULL, null)*.definition()*.name() ==
                registry.resolveForProfile(ToolProfile.FULL)*.definition()*.name()
    }

    def "a deferred tool is withheld until the session discovers it"() {
        given:
        def registry = new ToolRegistry()
        registry.registerAll([tool("visible"), tool("hidden")])
        registry.markDeferred { it.name() == "hidden" }

        expect: "not sent up front"
        registry.resolveActive(ToolProfile.FULL, null)*.definition()*.name() == ["visible"]

        and: "sent once discovered"
        registry.resolveActive(ToolProfile.FULL, ["hidden"] as Set)*.definition()*.name()
                .toSorted() == ["hidden", "visible"]
    }

    def "deferral can target a whole section or source at once"() {
        given:
        def registry = new ToolRegistry()
        registry.registerAll([
                tool("core_a"), tool("core_b"),
                tool("mcp_x", "remote", ToolDefinition.SOURCE_MCP),
                tool("mcp_y", "remote", ToolDefinition.SOURCE_MCP),
        ])

        when: "defer everything that came from MCP"
        int count = registry.markDeferred { it.source() == ToolDefinition.SOURCE_MCP }

        then:
        count == 2
        registry.resolveActive(ToolProfile.FULL, null)*.definition()*.name().toSorted() ==
                ["core_a", "core_b"]
    }

    def "markDeferred reports only newly deferred tools"() {
        given:
        def registry = new ToolRegistry()
        registry.registerAll([tool("a"), tool("b")])

        expect:
        registry.markDeferred { it.name() == "a" } == 1
        registry.markDeferred { it.name() == "a" } == 0
        registry.markDeferred { true } == 1
    }

    def "a tool that declares itself deferred is honoured without configuration"() {
        given:
        def registry = new ToolRegistry()
        def selfDeferred = new ToolCallback() {
            ToolDefinition definition() {
                ToolDefinition.builder().name("noisy").description("d").section("s")
                        .inputSchema('{}').profiles([ToolProfile.FULL] as Set)
                        .deferred(true).build()
            }
            ToolResult execute(Map<String, Object> p, ToolContext c) { new ToolResult.Success("ok") }
        }
        registry.register(selfDeferred)

        expect:
        registry.isDeferred("noisy")
        registry.resolveActive(ToolProfile.FULL, null).isEmpty()
    }

    def "deferral never widens what a profile permits"() {
        given: "a deferred tool that the MINIMAL profile does not allow"
        def registry = new ToolRegistry()
        registry.register(tool("privileged", "exec", ToolDefinition.SOURCE_BUILTIN,
                [ToolProfile.CODING] as Set))
        registry.markDeferred { true }

        expect: "discovering it does not bypass profile filtering"
        registry.resolveActive(ToolProfile.MINIMAL, ["privileged"] as Set).isEmpty()
    }

    def "search is bounded by the run's profile"() {
        given:
        def registry = new ToolRegistry()
        registry.register(tool("coding_only", "exec", ToolDefinition.SOURCE_BUILTIN,
                [ToolProfile.CODING] as Set))

        expect: "search cannot be used to enumerate tools the run may not call"
        registry.search("coding_only", ToolProfile.CODING, 5)*.name() == ["coding_only"]
        registry.search("coding_only", ToolProfile.MINIMAL, 5).isEmpty()
    }

    def "clearDeferred and clearAllDeferred restore up-front visibility"() {
        given:
        def registry = new ToolRegistry()
        registry.registerAll([tool("a"), tool("b")])
        registry.markDeferred { true }

        when:
        registry.clearDeferred("a")

        then:
        registry.resolveActive(ToolProfile.FULL, null)*.definition()*.name() == ["a"]

        when:
        registry.clearAllDeferred()

        then:
        registry.resolveActive(ToolProfile.FULL, null).size() == 2
        registry.deferredNames().isEmpty()
    }

    def "unregister and clear drop deferral state"() {
        given:
        def registry = new ToolRegistry()
        registry.registerAll([tool("a"), tool("b")])
        registry.markDeferred { true }

        when:
        registry.unregister("a")

        then: "no stale deferral for a tool that no longer exists"
        !registry.isDeferred("a")

        when:
        registry.clear()

        then:
        registry.deferredNames().isEmpty()
        registry.size() == 0
    }

    def "the search index reflects registrations made after the first search"() {
        given:
        def registry = new ToolRegistry()
        registry.register(tool("file_read"))

        when: "a search warms the index, then a new tool arrives"
        registry.search("file", ToolProfile.FULL, 5)
        registry.register(tool("file_write"))

        then: "the index was invalidated, not left stale"
        registry.search("file", ToolProfile.FULL, 5)*.name().toSorted() == ["file_read", "file_write"]
    }

    def "resolveActiveForPolicy honours allow and deny alongside deferral"() {
        given:
        def registry = new ToolRegistry()
        registry.registerAll([tool("a"), tool("b"), tool("c")])
        registry.markDeferred { it.name() == "c" }

        expect: "deny still removes, and a deferred tool stays hidden until discovered"
        registry.resolveActiveForPolicy(ToolProfile.FULL, [], ["b"], null)*.definition()*.name() == ["a"]
        registry.resolveActiveForPolicy(ToolProfile.FULL, [], ["b"], ["c"] as Set)*.definition()*.name()
                .toSorted() == ["a", "c"]
    }
}
