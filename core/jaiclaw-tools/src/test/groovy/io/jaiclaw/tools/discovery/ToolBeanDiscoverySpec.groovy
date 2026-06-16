package io.jaiclaw.tools.discovery

import io.jaiclaw.core.tool.ToolCallback
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolDefinition
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.tools.ToolRegistry
import spock.lang.Specification

class ToolBeanDiscoverySpec extends Specification {

    ToolRegistry registry = new ToolRegistry()
    ToolBeanDiscovery discovery = new ToolBeanDiscovery(registry)

    def "null input is a no-op"() {
        expect:
        discovery.discoverAndRegister(null) == 0
        registry.size() == 0
    }

    def "empty input is a no-op"() {
        expect:
        discovery.discoverAndRegister([]) == 0
        registry.size() == 0
    }

    def "null elements are skipped"() {
        when:
        int count = discovery.discoverAndRegister([null, stubTool("foo"), null])

        then:
        count == 1
        registry.contains("foo")
        registry.size() == 1
    }

    def "registers a single bean and returns count"() {
        given:
        def t = stubTool("hello")

        when:
        int count = discovery.discoverAndRegister([t])

        then:
        count == 1
        registry.resolve("hello").isPresent()
        registry.resolve("hello").get().is(t)
    }

    def "registers multiple distinct beans"() {
        when:
        int count = discovery.discoverAndRegister([
                stubTool("a"), stubTool("b"), stubTool("c")
        ])

        then:
        count == 3
        registry.size() == 3
        registry.contains("a")
        registry.contains("b")
        registry.contains("c")
    }

    def "fails fast when two beans share the same tool name"() {
        given:
        def first = stubTool("dup", "first")
        def second = stubTool("dup", "second")

        when:
        discovery.discoverAndRegister([first, second])

        then:
        IllegalStateException ex = thrown()
        ex.message.contains("Duplicate tool name 'dup'")
        ex.message.contains(first.getClass().getName())
        ex.message.contains(second.getClass().getName())
    }

    def "fails fast when a bean collides with a pre-registered tool"() {
        given:
        def existing = stubTool("web_search", "built-in")
        registry.register(existing)
        def newBean = stubTool("web_search", "override")

        when:
        discovery.discoverAndRegister([newBean])

        then:
        IllegalStateException ex = thrown()
        ex.message.contains("Tool name collision: 'web_search'")
        ex.message.contains(existing.getClass().getName())
        ex.message.contains(newBean.getClass().getName())
        ex.message.contains("@ConditionalOnMissingBean")
    }

    def "preserves the first registration when a later bean collides — registry not mutated past the conflict"() {
        given:
        def ok = stubTool("ok")
        def conflict1 = stubTool("boom", "first")
        def conflict2 = stubTool("boom", "second")

        when:
        discovery.discoverAndRegister([ok, conflict1, conflict2])

        then:
        IllegalStateException ex = thrown()
        ex.message.contains("Duplicate tool name 'boom'")
        // ok registered before the failure; conflict1 registered; conflict2 rejected
        registry.contains("ok")
        registry.contains("boom")
        registry.resolve("boom").get().is(conflict1)
        registry.size() == 2
    }

    def "fails fast when a bean has a null or blank tool name"() {
        when:
        discovery.discoverAndRegister([blankNamedTool()])

        then:
        IllegalStateException ex = thrown()
        ex.message.contains("null or blank definition().name()")
    }

    def "constructor rejects null registry"() {
        when:
        new ToolBeanDiscovery(null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("registry must not be null")
    }

    // --- helpers -----------------------------------------------------------

    private ToolCallback stubTool(String name, String description = "desc") {
        def definition = new ToolDefinition(name, description, "Test",
                '{"type":"object","properties":{},"required":[]}', Set.of(ToolProfile.FULL))
        return new ToolCallback() {
            @Override
            ToolDefinition definition() { return definition }

            @Override
            ToolResult execute(Map<String, Object> parameters, ToolContext context) {
                return new ToolResult.Success("ok")
            }
        }
    }

    private ToolCallback blankNamedTool() {
        def definition = new ToolDefinition("", "blank", "Test",
                '{"type":"object","properties":{},"required":[]}', Set.of(ToolProfile.FULL))
        return new ToolCallback() {
            @Override
            ToolDefinition definition() { return definition }

            @Override
            ToolResult execute(Map<String, Object> parameters, ToolContext context) {
                return new ToolResult.Success("ok")
            }
        }
    }
}
