package io.jaiclaw.tools.builtin.render

import io.jaiclaw.asciirender.skill.RenderableTemplate
import io.jaiclaw.asciirender.skill.RenderableTemplateRegistry
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

class RenderResponseToolSpec extends Specification {

    // ToolContext is a record — can't Spock-mock it. Real instances are cheap.
    ToolContext ctx = new ToolContext("agent", "session", "sid", "/tmp")

    def "empty registry: tool loads with empty schema.enum, errors on every call"() {
        given:
        def tool = new RenderResponseTool(new RenderableTemplateRegistry([]))

        expect: "schema exists with empty enum"
        tool.definition().name() == "render_response"
        tool.definition().inputSchema().contains('"enum": []')

        when:
        def result = tool.execute([template: "anything"], ctx)

        then: "always errors — no templates registered"
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("no RenderableTemplate beans registered")
    }

    def "two templates: schema enum lists both + dispatch routes to the right one"() {
        given:
        def a = stubTemplate("event_card", ["event_id"] as Set, "RENDERED-A")
        def b = stubTemplate("task_kanban", ["task_id", "status"] as Set, "RENDERED-B")
        def tool = new RenderResponseTool(new RenderableTemplateRegistry([a, b]))

        expect: "schema advertises both names + all their params"
        tool.definition().inputSchema().contains('"event_card"')
        tool.definition().inputSchema().contains('"task_kanban"')
        tool.definition().inputSchema().contains('"event_id"')
        tool.definition().inputSchema().contains('"task_id"')
        tool.definition().inputSchema().contains('"status"')

        when: "dispatch to a"
        def resultA = tool.execute([template: "event_card", event_id: "evt-1"], ctx)

        then:
        resultA instanceof ToolResult.Success
        ((ToolResult.Success) resultA).content() == "RENDERED-A"

        when: "dispatch to b"
        def resultB = tool.execute([template: "task_kanban", task_id: "t-1", status: "done"], ctx)

        then:
        resultB instanceof ToolResult.Success
        ((ToolResult.Success) resultB).content() == "RENDERED-B"
    }

    def "unknown template: ToolResult.Error naming the known names"() {
        given:
        def known = stubTemplate("event_card", ["event_id"] as Set, "OK")
        def tool = new RenderResponseTool(new RenderableTemplateRegistry([known]))

        when:
        def result = tool.execute([template: "not_a_real_template"], ctx)

        then:
        result instanceof ToolResult.Error
        def msg = ((ToolResult.Error) result).message()
        msg.contains("unknown template 'not_a_real_template'")
        msg.contains("event_card")
    }

    def "template that throws is caught and surfaced as ToolResult.Error (not propagated)"() {
        given: "a template that violates the SPI contract and throws"
        def broken = new RenderableTemplate() {
            @Override String name() { "broken" }
            @Override String description() { "throws to test the safety net" }
            @Override Set<String> parameterNames() { [] as Set }
            @Override String render(Map<String, Object> p) {
                throw new IllegalStateException("boom")
            }
        }
        def tool = new RenderResponseTool(new RenderableTemplateRegistry([broken]))

        when:
        def result = tool.execute([template: "broken"], ctx)

        then: "tool call doesn't crash; LLM gets an error it can react to"
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("broken")
        ((ToolResult.Error) result).message().contains("IllegalStateException")
    }

    private static RenderableTemplate stubTemplate(String name, Set<String> params, String output) {
        return new RenderableTemplate() {
            @Override String name() { name }
            @Override String description() { "stub" }
            @Override Set<String> parameterNames() { params }
            @Override String render(Map<String, Object> p) { output }
        }
    }
}
