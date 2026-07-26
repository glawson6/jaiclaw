package io.jaiclaw.pipeline.tool

import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.pipeline.render.PipelineRenderService
import io.jaiclaw.pipeline.render.RenderProfile
import spock.lang.Specification

class PipelineRenderToolSpec extends Specification {

    PipelineRenderService service = Mock()
    PipelineRenderTool tool = new PipelineRenderTool(service)

    def "definition metadata exposes tool name + input schema"() {
        expect:
        tool.definition().name() == "pipeline_render"
        tool.definition().description().contains("pipeline")
        tool.definition().inputSchema().contains("\"pipelineId\"")
        tool.definition().inputSchema().contains("compact")
        tool.definition().inputSchema().contains("table")
        tool.definition().inputSchema().contains("flow")
    }

    def "defaults to view=compact and profile=shell_80 when not specified"() {
        given:
        service.renderAscii("p1", null,
                PipelineRenderService.View.COMPACT,
                RenderProfile.SHELL_80) >> "OK"

        when:
        def result = tool.execute([pipelineId: "p1"], null)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content() == "OK"
    }

    def "passes explicit view + profile to the service"() {
        given:
        service.renderAscii("p1", "exec-9",
                PipelineRenderService.View.TABLE,
                RenderProfile.SLACK_MOBILE) >> "TABLE"

        when:
        def result = tool.execute([
                pipelineId: "p1",
                executionId: "exec-9",
                view: "table",
                profile: "slack_mobile"], null)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content() == "TABLE"
    }

    def "unknown pipelineId translates to ToolResult.Error"() {
        given:
        service.renderAscii("nope", _, _, _) >> {
            throw new IllegalArgumentException("Unknown pipeline id: nope")
        }

        when:
        def result = tool.execute([pipelineId: "nope"], null)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Unknown pipeline id")
    }

    def "missing pipelineId returns ToolResult.Error"() {
        when:
        def result = tool.execute([:], null)

        then:
        result instanceof ToolResult.Error
    }
}
