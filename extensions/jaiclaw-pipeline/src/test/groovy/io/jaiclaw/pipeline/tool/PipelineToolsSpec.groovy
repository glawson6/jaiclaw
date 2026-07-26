package io.jaiclaw.pipeline.tool

import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.PipelineRegistry
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.pipeline.TriggerDefinition
import io.jaiclaw.pipeline.TriggerType
import io.jaiclaw.pipeline.ErrorStrategy
import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle
import io.jaiclaw.pipeline.gateway.PipelineExecutionResult
import io.jaiclaw.pipeline.gateway.PipelineGateway
import io.jaiclaw.pipeline.tracking.ExecutionStatus
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker
import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.util.Optional

class PipelineToolsSpec extends Specification {

    PipelineGateway gateway = Mock()
    PipelineRegistry registry = Mock()
    PipelineExecutionTracker tracker = Mock()

    def pipelineDef(String id) {
        new PipelineDefinition(
                id, id + " name", "desc", [], true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "beanA", null, null, null, null, null, null, null, null)],
                null, null)
    }

    def summary(String executionId, String pipelineId, ExecutionStatus status) {
        new PipelineExecutionSummary(
                executionId, pipelineId, "tenant-x",
                Instant.now().minusSeconds(2), Instant.now(),
                status, null, [:], null, Duration.ofSeconds(2))
    }

    // ── PipelineListTool ────────────────────────────────────────

    def "PipelineListTool serializes all registered pipelines"() {
        given:
        registry.getAll() >> [pipelineDef("p1"), pipelineDef("p2")]
        def tool = new PipelineListTool(registry)

        when:
        ToolResult result = tool.execute([:], null)

        then:
        result instanceof ToolResult.Success
        def json = ((ToolResult.Success) result).content()
        json.contains('"count":2')
        json.contains('"id":"p1"')
        json.contains('"id":"p2"')
        json.contains('"stageNames"')
    }

    def "PipelineListTool returns empty list when registry is empty"() {
        given:
        registry.getAll() >> []
        def tool = new PipelineListTool(registry)

        when:
        ToolResult result = tool.execute([:], null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains('"count":0')
    }

    // ── PipelineTriggerTool ─────────────────────────────────────

    def "PipelineTriggerTool fire-and-forget returns the handle as JSON"() {
        given:
        def tool = new PipelineTriggerTool(gateway)
        def handle = new PipelineExecutionHandle("exec-1", "p1", Instant.now())
        gateway.trigger("p1", "hello") >> handle

        when:
        ToolResult result = tool.execute([pipelineId: "p1", input: "hello"], null)

        then:
        result instanceof ToolResult.Success
        def json = ((ToolResult.Success) result).content()
        json.contains('"executionId":"exec-1"')
        json.contains('"pipelineId":"p1"')
        json.contains('"status":"SUBMITTED"')
    }

    def "PipelineTriggerTool with awaitSeconds > 0 returns the completed result"() {
        given:
        def tool = new PipelineTriggerTool(gateway)
        def result = PipelineExecutionResult.success(
                new io.jaiclaw.pipeline.PipelineContext("p1", "exec-2", null, null, 1, 1, null, null, [:], [:]),
                Instant.now(), Instant.now(), Duration.ofMillis(50))
        gateway.triggerAndAwait("p1", "hi", null, null, Duration.ofSeconds(5)) >> result

        when:
        ToolResult r = tool.execute([pipelineId: "p1", input: "hi", awaitSeconds: 5], null)

        then:
        r instanceof ToolResult.Success
        def json = ((ToolResult.Success) r).content()
        json.contains('"executionId":"exec-2"')
    }

    def "PipelineTriggerTool rejects a call missing pipelineId"() {
        given:
        def tool = new PipelineTriggerTool(gateway)

        when:
        ToolResult result = tool.execute([input: "hi"], null)

        then:
        result instanceof ToolResult.Error
    }

    // ── PipelineStatusTool ──────────────────────────────────────

    def "PipelineStatusTool returns the summary as JSON"() {
        given:
        def tool = new PipelineStatusTool(tracker)
        tracker.byId("exec-3") >> Optional.of(summary("exec-3", "p1", ExecutionStatus.SUCCESS))

        when:
        ToolResult result = tool.execute([executionId: "exec-3"], null)

        then:
        result instanceof ToolResult.Success
        def json = ((ToolResult.Success) result).content()
        json.contains('"executionId":"exec-3"')
        json.contains('"status":"SUCCESS"')
    }

    def "PipelineStatusTool returns Error when the executionId isn't tracked"() {
        given:
        def tool = new PipelineStatusTool(tracker)
        tracker.byId("missing") >> Optional.empty()

        when:
        ToolResult result = tool.execute([executionId: "missing"], null)

        then:
        result instanceof ToolResult.Error
    }

    // ── PipelineTools factory ───────────────────────────────────

    def "PipelineTools.all(gateway, registry, tracker, null) wires three tools when no render service"() {
        when:
        def all = PipelineTools.all(gateway, registry, tracker, null)

        then:
        all.size() == 3
        all[0] instanceof PipelineListTool
        all[1] instanceof PipelineTriggerTool
        all[2] instanceof PipelineStatusTool
    }

    def "PipelineTools.all wires four tools when a render service is present"() {
        given:
        def renderService = Stub(io.jaiclaw.pipeline.render.PipelineRenderService)

        when:
        def all = PipelineTools.all(gateway, registry, tracker, renderService)

        then:
        all.size() == 4
        all[0] instanceof PipelineListTool
        all[1] instanceof PipelineTriggerTool
        all[2] instanceof PipelineStatusTool
        all[3] instanceof PipelineRenderTool
    }
}
