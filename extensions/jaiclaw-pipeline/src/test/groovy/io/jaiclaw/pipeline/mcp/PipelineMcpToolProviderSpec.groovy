package io.jaiclaw.pipeline.mcp

import io.jaiclaw.core.mcp.McpToolResult
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.PipelineRegistry
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.pipeline.TriggerDefinition
import io.jaiclaw.pipeline.TriggerType
import io.jaiclaw.pipeline.ErrorStrategy
import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle
import io.jaiclaw.pipeline.gateway.PipelineGateway
import io.jaiclaw.pipeline.tracking.ExecutionStatus
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class PipelineMcpToolProviderSpec extends Specification {

    PipelineGateway gateway = Mock()
    PipelineRegistry registry = Mock()
    PipelineExecutionTracker tracker = Mock()
    io.jaiclaw.pipeline.render.PipelineRenderService renderService = Mock()
    PipelineMcpToolProvider provider = new PipelineMcpToolProvider(
            gateway, registry, tracker, renderService)

    def "server name and description are exposed"() {
        expect:
        provider.getServerName() == "pipeline"
        provider.getServerDescription() == "Trigger and inspect JaiClaw pipelines"
    }

    def "getTools returns five tool definitions when render service is present"() {
        when:
        def tools = provider.getTools()

        then:
        tools.size() == 5
        tools*.name().toSet() == ["pipeline_list", "pipeline_trigger", "pipeline_trigger_sync",
                                   "pipeline_status", "pipeline_render"] as Set
        tools.each { assert it.inputSchema()?.contains("\"type\": \"object\"") }
    }

    def "getTools omits pipeline_render when the render service is absent"() {
        given:
        def bareProvider = new PipelineMcpToolProvider(gateway, registry, tracker, null)

        when:
        def tools = bareProvider.getTools()

        then:
        tools.size() == 4
        !(tools*.name().contains("pipeline_render"))
    }

    def "pipeline_render dispatch delegates to render service"() {
        given:
        renderService.renderAscii("p1", null,
                io.jaiclaw.pipeline.render.PipelineRenderService.View.COMPACT,
                io.jaiclaw.pipeline.render.RenderProfile.SHELL_80) >> "RENDERED"

        when:
        def result = provider.execute("pipeline_render",
                [pipelineId: "p1"], null)

        then:
        !result.isError()
        result.content() == "RENDERED"
    }

    def "pipeline_render without pipelineId returns error"() {
        when:
        def result = provider.execute("pipeline_render", [:], null)

        then:
        result.isError()
    }

    def "unknown tool returns error"() {
        when:
        McpToolResult result = provider.execute("pipeline_wat", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }

    def "pipeline_list returns a JSON payload with pipelines"() {
        given:
        def def1 = new PipelineDefinition(
                "p1", "P1", "d", [], true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "beanA", null, null, null, null, null, null, null, null)],
                null, null)
        registry.getAll() >> [def1]

        when:
        McpToolResult r = provider.execute("pipeline_list", [:], null)

        then:
        !r.isError()
        r.content().contains('"id":"p1"')
        r.content().contains('"stageNames"')
    }

    def "pipeline_trigger without pipelineId returns error"() {
        when:
        McpToolResult r = provider.execute("pipeline_trigger", [:], null)

        then:
        r.isError()
        r.content().contains("pipelineId")
    }

    def "pipeline_trigger delegates to gateway.trigger and returns handle JSON"() {
        given:
        def handle = new PipelineExecutionHandle("exec-1", "p1", Instant.now())
        gateway.trigger("p1", "hello") >> handle

        when:
        McpToolResult r = provider.execute("pipeline_trigger",
                [pipelineId: "p1", input: "hello"], null)

        then:
        !r.isError()
        r.content().contains('"executionId":"exec-1"')
    }

    def "pipeline_status returns summary JSON for a known id"() {
        given:
        def s = new PipelineExecutionSummary(
                "exec-2", "p1", "tenant-x",
                Instant.now().minusSeconds(1), Instant.now(),
                ExecutionStatus.SUCCESS, null, [:], null, Duration.ofSeconds(1))
        tracker.byId("exec-2") >> Optional.of(s)

        when:
        McpToolResult r = provider.execute("pipeline_status", [executionId: "exec-2"], null)

        then:
        !r.isError()
        r.content().contains('"executionId":"exec-2"')
        r.content().contains('"status":"SUCCESS"')
    }

    def "pipeline_status returns Error when the id isn't tracked"() {
        given:
        tracker.byId("missing") >> Optional.empty()

        when:
        McpToolResult r = provider.execute("pipeline_status", [executionId: "missing"], null)

        then:
        r.isError()
    }
}
