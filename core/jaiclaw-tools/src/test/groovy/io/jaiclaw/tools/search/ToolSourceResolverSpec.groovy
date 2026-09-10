package io.jaiclaw.tools.search

import io.jaiclaw.core.tool.ToolCallback
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolDefinition
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.tools.ToolRegistry
import spock.lang.Specification

class ToolSourceResolverSpec extends Specification {

    private static ToolCallback tool(String name, String declaredSource = null) {
        def b = ToolDefinition.builder()
                .name(name).description("does $name").section("misc")
                .inputSchema('{"type":"object"}').profiles([ToolProfile.FULL] as Set)
        if (declaredSource) b.source(declaredSource)
        def def_ = b.build()
        new ToolCallback() {
            ToolDefinition definition() { def_ }
            ToolResult execute(Map<String, Object> p, ToolContext c) { new ToolResult.Success("ok") }
        }
    }

    def "a tool that declares its own source is believed"() {
        expect:
        ToolSourceResolver.resolve(tool("t", ToolDefinition.SOURCE_MCP)) == ToolDefinition.SOURCE_MCP
        ToolSourceResolver.resolve(tool("t", "custom-origin")) == "custom-origin"
    }

    def "package inference identifies the owning module"() {
        expect:
        ToolSourceResolver.fromPackage(fqn) == expected

        where:
        fqn                                                          | expected
        "io.jaiclaw.messaging.mcp.MessagingMcpToolProvider"          | "mcp"
        "io.jaiclaw.kanban.mcp.KanbanMcpToolProvider"                | "mcp"
        "io.jaiclaw.camel.CamelRouteTool"                            | "camel"
        "io.jaiclaw.kanban.tool.BoardTool"                           | "kanban"
        "io.jaiclaw.pipeline.tool.PipelineTriggerTool"               | "pipeline"
        "io.jaiclaw.tools.builtin.FileReadTool"                      | "builtin"
        "io.jaiclaw.core.something.Thing"                            | "builtin"
    }

    def "mcp is matched before the generic module segment"() {
        expect: "otherwise a kanban MCP provider would report 'kanban', not 'mcp'"
        ToolSourceResolver.fromPackage("io.jaiclaw.kanban.mcp.Provider") == ToolDefinition.SOURCE_MCP
    }

    def "unrecognised or malformed names fall back to builtin rather than throwing"() {
        expect: "a misfiled tool must still register"
        ToolSourceResolver.fromPackage(fqn) == ToolDefinition.SOURCE_BUILTIN

        where:
        fqn << [null, "", "   ", "com.acme.Whatever", "NoPackage"]
    }

    def "a null tool resolves to builtin"() {
        expect:
        ToolSourceResolver.resolve(null) == ToolDefinition.SOURCE_BUILTIN
    }

    // ── The bug this fixes ───────────────────────────────────────────────────

    def "sources deferral matches tools that never stamped a source themselves"() {
        given: "an anonymous tool class, exactly as ToolBeanDiscovery registers them"
        def registry = new ToolRegistry()
        registry.register(tool("plain_tool"))

        when: "the single-argument predicate is used, as the starter originally did"
        int viaDefinition = registry.markDeferred { def_ ->
            ToolDefinition.SOURCE_MCP.equals(def_.source())
        }

        then: "it matches nothing — the definition's source is the default"
        viaDefinition == 0

        when: "the resolved-source overload is used instead"
        int viaResolved = registry.markDeferred { def_, source ->
            source != null
        }

        then: "every tool is now reachable by a source rule"
        viaResolved == 1
    }

    def "sourceOf reports the resolved source for a registered tool"() {
        given:
        def registry = new ToolRegistry()
        registry.register(tool("declared", ToolDefinition.SOURCE_CAMEL))

        expect:
        registry.sourceOf("declared") == ToolDefinition.SOURCE_CAMEL
        registry.sourceOf("no-such-tool") == ToolDefinition.SOURCE_BUILTIN
    }

    def "a null predicate defers nothing"() {
        given:
        def registry = new ToolRegistry()
        registry.register(tool("a"))

        expect:
        registry.markDeferred((java.util.function.BiPredicate) null) == 0
    }
}
