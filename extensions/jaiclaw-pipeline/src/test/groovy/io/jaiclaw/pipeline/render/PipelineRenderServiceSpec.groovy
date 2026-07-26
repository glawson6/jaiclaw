package io.jaiclaw.pipeline.render

import io.jaiclaw.pipeline.ErrorStrategy
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.PipelineRegistry
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.pipeline.TriggerDefinition
import io.jaiclaw.pipeline.TriggerType
import io.jaiclaw.pipeline.tracking.ExecutionStatus
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class PipelineRenderServiceSpec extends Specification {

    PipelineRegistry registry = Mock()
    PipelineExecutionTracker tracker = Mock()
    PipelineAsciiRenderer asciiRenderer = new PipelineAsciiRenderer()
    PipelineHtmlRenderer htmlRenderer = new PipelineHtmlRenderer()
    PipelineRenderService service = new PipelineRenderService(registry, tracker, asciiRenderer, htmlRenderer)

    def "unknown pipeline id throws IllegalArgumentException"() {
        given:
        registry.get("nope") >> null

        when:
        service.renderAscii("nope", null, PipelineRenderService.View.COMPACT, RenderProfile.SHELL_80)

        then:
        thrown(IllegalArgumentException)
    }

    def "pipeline exists but no executions → renders with all PENDING"() {
        given:
        registry.get("p1") >> def_("p1", ["fetch", "persist"])
        tracker.recent("p1") >> []

        when:
        def out = service.renderAscii("p1", null, PipelineRenderService.View.COMPACT, RenderProfile.SHELL_80)

        then:
        out.contains("[·] fetch")
        out.contains("[·] persist")
        out.contains("Status: NOT-RUN")
    }

    def "resolves latest execution when executionId is null"() {
        given:
        registry.get("p1") >> def_("p1", ["fetch"])
        tracker.recent("p1") >> [summarySuccess("exec-latest", "p1")]

        when:
        def out = service.renderAscii("p1", null, PipelineRenderService.View.COMPACT, RenderProfile.SHELL_80)

        then:
        out.contains("Status: SUCCESS")
        out.contains("exec-l")
    }

    def "specific executionId lookup goes through tracker.byId"() {
        given:
        registry.get("p1") >> def_("p1", ["fetch"])
        tracker.byId("exec-42") >> Optional.of(summarySuccess("exec-42", "p1"))

        when:
        def out = service.renderAscii("p1", "exec-42", PipelineRenderService.View.COMPACT, RenderProfile.SHELL_80)

        then:
        out.contains("exec-4")
    }

    def "pipelineExists true when registry has it"() {
        given:
        registry.get("p1") >> def_("p1", ["fetch"])
        registry.get("nope") >> null

        expect:
        service.pipelineExists("p1")
        !service.pipelineExists("nope")
        !service.pipelineExists(null)
        !service.pipelineExists("")
    }

    def "mostRecentExecutionId returns the top of the ring buffer"() {
        given:
        tracker.recent("p1") >> [summarySuccess("newer", "p1"), summarySuccess("older", "p1")]

        when:
        def result = service.mostRecentExecutionId("p1")

        then:
        result.isPresent()
        result.get() == "newer"
    }

    def "renderHtml with FLOW view respects the format param"() {
        given:
        registry.get("p1") >> def_("p1", ["s1", "s2"])
        tracker.recent("p1") >> []

        when:
        def divOut = service.renderHtml("p1", null, PipelineRenderService.View.FLOW, PipelineHtmlRenderer.FlowFormat.DIV)
        def svgOut = service.renderHtml("p1", null, PipelineRenderService.View.FLOW, PipelineHtmlRenderer.FlowFormat.SVG)

        then:
        divOut.contains("jaiclaw-pipeline--flow")
        !divOut.contains("<svg")
        svgOut.contains("jaiclaw-pipeline--flow-svg")
        svgOut.contains("<svg")
    }

    def "View.fromString parses case-insensitively and defaults to COMPACT"() {
        expect:
        PipelineRenderService.View.fromString("compact") == PipelineRenderService.View.COMPACT
        PipelineRenderService.View.fromString("TABLE") == PipelineRenderService.View.TABLE
        PipelineRenderService.View.fromString("Flow") == PipelineRenderService.View.FLOW
        PipelineRenderService.View.fromString(null) == PipelineRenderService.View.COMPACT
        PipelineRenderService.View.fromString("junk") == PipelineRenderService.View.COMPACT
    }

    // ── fixtures ───────────────────────────────────────

    private PipelineDefinition def_(String id, List<String> stageNames) {
        def stages = stageNames.collect { name ->
            new StageDefinition(name, StageType.PROCESSOR, "bean-" + name,
                    null, null, null, null, null, null, null, null)
        }
        return new PipelineDefinition(id, id, null, [], true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null, stages, null, null)
    }

    private PipelineExecutionSummary summarySuccess(String execId, String pipelineId) {
        return new PipelineExecutionSummary(
                execId, pipelineId, null,
                Instant.now().minusSeconds(1), Instant.now(),
                ExecutionStatus.SUCCESS, null,
                ["fetch": Duration.ofMillis(500)], null, Duration.ofMillis(500))
    }
}
