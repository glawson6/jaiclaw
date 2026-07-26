package io.jaiclaw.shell.commands

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
import org.springframework.beans.factory.ObjectProvider
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class PipelineCommandSpec extends Specification {

    PipelineGateway gateway = Mock()
    PipelineRegistry registry = Mock()
    PipelineExecutionTracker tracker = Mock()

    ObjectProvider<PipelineGateway> gatewayProvider = Stub {
        getIfAvailable() >> gateway
    }
    ObjectProvider<PipelineRegistry> registryProvider = Stub {
        getIfAvailable() >> registry
    }
    ObjectProvider<PipelineExecutionTracker> trackerProvider = Stub {
        getIfAvailable() >> tracker
    }
    ObjectProvider<io.jaiclaw.pipeline.render.PipelineRenderService> renderServiceProvider = Stub {
        getIfAvailable() >> null
    }

    PipelineCommand cmd = new PipelineCommand(
            gatewayProvider, registryProvider, trackerProvider, renderServiceProvider,
            "http://localhost:8080")

    ObjectProvider<PipelineGateway> emptyGatewayProvider = Stub {
        getIfAvailable() >> null
    }
    ObjectProvider<PipelineRegistry> emptyRegistryProvider = Stub {
        getIfAvailable() >> null
    }
    ObjectProvider<PipelineExecutionTracker> emptyTrackerProvider = Stub {
        getIfAvailable() >> null
    }

    def pipelineDef(String id) {
        new PipelineDefinition(
                id, id + " display", "desc", [], true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "beanA", null, null, null, null, null, null, null, null)],
                null, null)
    }

    def "list uses the local registry when available"() {
        given:
        registry.getAll() >> [pipelineDef("p1"), pipelineDef("p2")]

        when:
        String output = cmd.list()

        then:
        output.contains("p1")
        output.contains("p2")
        output.contains("2 pipeline(s).")
    }

    def "list falls back to remote when the local registry is absent"() {
        given:
        PipelineCommand remote = new PipelineCommand(
                emptyGatewayProvider, emptyRegistryProvider, emptyTrackerProvider,
                renderServiceProvider,
                "http://127.0.0.1:1")   // unreachable — exercises the failure path

        when:
        String output = remote.list()

        then:
        output.contains("Could not reach")
    }

    def "trigger fire-and-forget prints the handle"() {
        given:
        gateway.trigger("p1", "hi") >> new PipelineExecutionHandle(
                "exec-9", "p1", Instant.parse("2026-07-15T10:00:00Z"))

        when:
        String output = cmd.trigger("p1", "hi", 0, "", "")

        then:
        output.contains("Submitted:")
        output.contains("exec-9")
        output.contains("p1")
    }

    def "status prints the summary when found"() {
        given:
        tracker.byId("exec-9") >> Optional.of(new PipelineExecutionSummary(
                "exec-9", "p1", "tenant-x",
                Instant.parse("2026-07-15T10:00:00Z"),
                Instant.parse("2026-07-15T10:00:01Z"),
                ExecutionStatus.SUCCESS, null, [:], null, Duration.ofSeconds(1)))

        when:
        String output = cmd.status("exec-9")

        then:
        output.contains("Execution SUCCESS")
        output.contains("exec-9")
        output.contains("tenant-x")
    }

    def "status reports not-found when tracker is empty"() {
        given:
        tracker.byId("gone") >> Optional.empty()

        when:
        String output = cmd.status("gone")

        then:
        output.contains("not found")
    }

    def "recent prints a table with the latest N summaries"() {
        given:
        def rows = (1..3).collect { i ->
            new PipelineExecutionSummary(
                    "exec-${i}", "p1", "tenant-x",
                    Instant.parse("2026-07-15T10:00:0${i}Z"),
                    Instant.parse("2026-07-15T10:00:0${i + 1}Z"),
                    ExecutionStatus.SUCCESS, null, [:], null, Duration.ofSeconds(1))
        }
        tracker.recent("p1") >> rows

        when:
        String output = cmd.recent("p1", 2)

        then:
        output.contains("EXECUTION ID")
        output.contains("exec-1")
        output.contains("exec-2")
        !output.contains("exec-3")
        output.contains("2 execution(s).")
    }

    def "render uses local render service when available"() {
        given:
        def renderService = Mock(io.jaiclaw.pipeline.render.PipelineRenderService)
        renderService.renderAscii("p1", null,
                io.jaiclaw.pipeline.render.PipelineRenderService.View.COMPACT,
                io.jaiclaw.pipeline.render.RenderProfile.SHELL_80) >> "RENDERED-ASCII"
        ObjectProvider<io.jaiclaw.pipeline.render.PipelineRenderService> provider = Stub {
            getIfAvailable() >> renderService
        }
        PipelineCommand cmdWithRender = new PipelineCommand(
                gatewayProvider, registryProvider, trackerProvider, provider,
                "http://localhost:8080")

        when:
        String output = cmdWithRender.render("p1", "", "compact", "shell_80", "div", false)

        then:
        output == "RENDERED-ASCII"
    }

    def "render --html hits the html render path"() {
        given:
        def renderService = Mock(io.jaiclaw.pipeline.render.PipelineRenderService)
        renderService.renderHtml("p1", null,
                io.jaiclaw.pipeline.render.PipelineRenderService.View.FLOW,
                io.jaiclaw.pipeline.render.PipelineHtmlRenderer.FlowFormat.SVG) >> "<svg>...</svg>"
        ObjectProvider<io.jaiclaw.pipeline.render.PipelineRenderService> provider = Stub {
            getIfAvailable() >> renderService
        }
        PipelineCommand cmdWithRender = new PipelineCommand(
                gatewayProvider, registryProvider, trackerProvider, provider,
                "http://localhost:8080")

        when:
        String output = cmdWithRender.render("p1", "", "flow", "shell_80", "svg", true)

        then:
        output == "<svg>...</svg>"
    }

    def "render falls back to remote HTTP when local service is absent"() {
        given:
        PipelineCommand cmdNoRender = new PipelineCommand(
                gatewayProvider, registryProvider, trackerProvider, renderServiceProvider,
                "http://127.0.0.1:1")  // unreachable — exercises the failure path

        when:
        String output = cmdNoRender.render("p1", "", "compact", "shell_80", "div", false)

        then:
        output.contains("unavailable")
    }
}
