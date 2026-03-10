package io.jclaw.tools

import io.jclaw.core.tool.ToolCallback
import io.jclaw.core.tool.ToolContext
import io.jclaw.core.tool.ToolDefinition
import io.jclaw.core.tool.ToolProfile
import io.jclaw.core.tool.ToolResult
import spock.lang.Specification

class ToolRegistrySpec extends Specification {

    ToolRegistry registry = new ToolRegistry()

    def "register and resolve a tool by name"() {
        given:
        def tool = stubTool("my_tool", "A test tool", "Test", ToolProfile.FULL)

        when:
        registry.register(tool)

        then:
        registry.resolve("my_tool").isPresent()
        registry.resolve("my_tool").get() == tool
    }

    def "resolve returns empty for unknown tool"() {
        expect:
        registry.resolve("nonexistent").isEmpty()
    }

    def "register overwrites tool with same name"() {
        given:
        def tool1 = stubTool("dup", "First", "Test", ToolProfile.FULL)
        def tool2 = stubTool("dup", "Second", "Test", ToolProfile.FULL)

        when:
        registry.register(tool1)
        registry.register(tool2)

        then:
        registry.size() == 1
        registry.resolve("dup").get().definition().description() == "Second"
    }

    def "unregister removes a tool"() {
        given:
        registry.register(stubTool("removeme", "desc", "Test", ToolProfile.FULL))

        when:
        def removed = registry.unregister("removeme")

        then:
        removed
        !registry.contains("removeme")
        registry.size() == 0
    }

    def "unregister returns false for unknown tool"() {
        expect:
        !registry.unregister("ghost")
    }

    def "registerAll adds multiple tools"() {
        given:
        def tools = [
            stubTool("a", "A", "Test", ToolProfile.FULL),
            stubTool("b", "B", "Test", ToolProfile.FULL),
            stubTool("c", "C", "Test", ToolProfile.FULL),
        ]

        when:
        registry.registerAll(tools)

        then:
        registry.size() == 3
        registry.toolNames() == ["a", "b", "c"] as Set
    }

    def "resolveAll returns all registered tools"() {
        given:
        registry.register(stubTool("x", "X", "Test", ToolProfile.FULL))
        registry.register(stubTool("y", "Y", "Test", ToolProfile.FULL))

        when:
        def all = registry.resolveAll()

        then:
        all.size() == 2
    }

    def "resolveForProfile filters by profile"() {
        given:
        registry.register(stubTool("minimal_tool", "M", "Test", ToolProfile.MINIMAL))
        registry.register(stubTool("coding_tool", "C", "Test", ToolProfile.CODING))
        registry.register(stubTool("full_only_tool", "F", "Test", ToolProfile.FULL))

        when:
        def coding = registry.resolveForProfile(ToolProfile.CODING)

        then: "only tools tagged with CODING are included"
        coding.collect { it.definition().name() } == ["coding_tool"]

        when:
        def full = registry.resolveForProfile(ToolProfile.FULL)

        then: "FULL profile returns all tools"
        full.size() == 3
    }

    def "resolveBySection filters by section"() {
        given:
        registry.register(stubTool("file1", "F1", "Files", ToolProfile.FULL))
        registry.register(stubTool("web1", "W1", "Web", ToolProfile.FULL))
        registry.register(stubTool("file2", "F2", "Files", ToolProfile.FULL))

        when:
        def files = registry.resolveBySection("Files")

        then:
        files.size() == 2
        files.every { it.definition().section() == "Files" }
    }

    def "clear removes all tools"() {
        given:
        registry.register(stubTool("a", "A", "T", ToolProfile.FULL))
        registry.register(stubTool("b", "B", "T", ToolProfile.FULL))

        when:
        registry.clear()

        then:
        registry.size() == 0
        registry.toolNames().isEmpty()
    }

    def "contains checks tool existence"() {
        given:
        registry.register(stubTool("exists", "E", "T", ToolProfile.FULL))

        expect:
        registry.contains("exists")
        !registry.contains("nope")
    }

    private ToolCallback stubTool(String name, String description, String section, ToolProfile profile) {
        def definition = new ToolDefinition(name, description, section, '{"type":"object","properties":{},"required":[]}', Set.of(profile))
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
