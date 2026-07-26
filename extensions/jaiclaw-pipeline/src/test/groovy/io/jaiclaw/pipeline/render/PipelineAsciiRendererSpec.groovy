package io.jaiclaw.pipeline.render

import io.jaiclaw.pipeline.ErrorStrategy
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.pipeline.TriggerDefinition
import io.jaiclaw.pipeline.TriggerType
import io.jaiclaw.pipeline.tracking.ExecutionStatus
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class PipelineAsciiRendererSpec extends Specification {

    PipelineAsciiRenderer renderer = new PipelineAsciiRenderer()

    // ── compact ────────────────────────────────────────────

    def "compact view has header, one line per stage, footer with status"() {
        given:
        def vm = vmSuccessful()

        when:
        def out = renderer.renderCompact(vm, RenderProfile.SHELL_80)

        then:
        out.contains("Pipeline: nightly-price-sync")
        out.contains("(exec 8a9c2f")
        out.contains("[✓] fetch")
        out.contains("[✓] normalize")
        out.contains("3.4s")
        out.contains("Status: SUCCESS")
    }

    def "compact running shows stage x/N + running marker"() {
        given:
        def vm = vmRunning()

        when:
        def out = renderer.renderCompact(vm, RenderProfile.SHELL_80)

        then:
        out.contains("[~] normalize")
        out.contains("running")
        out.contains("Status: RUNNING (stage 2/3)")
    }

    def "compact no-execution shows all stages PENDING"() {
        given:
        def vm = vmNoExecution()

        when:
        def out = renderer.renderCompact(vm, RenderProfile.SHELL_80)

        then:
        out.contains("[·] fetch")
        out.contains("Status: NOT-RUN")
    }

    // ── table ──────────────────────────────────────────────

    def "table view has Unicode box-drawing header and one row per stage"() {
        given:
        def vm = vmSuccessful()

        when:
        def out = renderer.renderTable(vm, RenderProfile.SHELL_120)

        then:
        out.contains("STAGE")
        out.contains("TYPE")
        out.contains("STATUS")
        out.contains("DURATION")
        out.contains("┌") && out.contains("┐")
        out.contains("├") && out.contains("┤")
        out.contains("└") && out.contains("┘")
        out.contains("fetch")
        out.contains("normalize")
        out.contains("PROCESSOR")
        out.contains("✓ DONE")
    }

    def "table under shell_80 fits within 80 char lines"() {
        given:
        def vm = vmSuccessful()

        when:
        def out = renderer.renderTable(vm, RenderProfile.SHELL_80)
        int longest = out.split("\n").collect { it.length() }.max() ?: 0

        then:
        longest <= 80
    }

    // ── flow ──────────────────────────────────────────────

    def "flow view stacks stage boxes with arrows between them"() {
        given:
        def vm = vmSuccessful()

        when:
        def out = renderer.renderFlow(vm, RenderProfile.SHELL_80)

        then:
        // 3 stages -> 3 boxes -> 2 arrow segments
        out.count("▼") == 2
        // Each box has top + bottom border + at least 2 content lines
        out.contains("┌") || out.contains("╔")
        out.contains("└") || out.contains("╚")
        out.contains("fetch")
        out.contains("PROCESSOR")
        out.contains("3.4s")
    }

    def "flow running stage draws a doubled border"() {
        given:
        def vm = vmRunning()

        when:
        def out = renderer.renderFlow(vm, RenderProfile.SHELL_80)

        then:
        out.contains("╔")   // top-left double corner
        out.contains("╗")
        out.contains("running")
    }

    // ── fixtures ───────────────────────────────────────────

    private PipelineViewModel vmSuccessful() {
        def def_ = def_("nightly-price-sync", ["fetch", "normalize", "persist"])
        def summary = new PipelineExecutionSummary(
                "8a9c2f1234", "nightly-price-sync", null,
                Instant.now().minusSeconds(10), Instant.now(),
                ExecutionStatus.SUCCESS, null,
                ["fetch": Duration.ofMillis(3400),
                 "normalize": Duration.ofMillis(1100),
                 "persist": Duration.ofMillis(3900)],
                null, Duration.ofMillis(8400))
        return PipelineViewModel.of(def_, summary)
    }

    private PipelineViewModel vmRunning() {
        def def_ = def_("nightly-price-sync", ["fetch", "normalize", "persist"])
        def summary = new PipelineExecutionSummary(
                "8a9c2f1234", "nightly-price-sync", null,
                Instant.now().minusSeconds(5), null,
                ExecutionStatus.RUNNING, "normalize",
                ["fetch": Duration.ofMillis(3400)],
                null, null)
        return PipelineViewModel.of(def_, summary)
    }

    private PipelineViewModel vmNoExecution() {
        return PipelineViewModel.of(def_("nightly-price-sync", ["fetch", "normalize"]), null)
    }

    private PipelineDefinition def_(String id, List<String> stageNames) {
        def stages = stageNames.collect { name ->
            new StageDefinition(name, StageType.PROCESSOR, "bean-" + name,
                    null, null, null, null, null, null, null, null)
        }
        return new PipelineDefinition(id, id + " display", null, [], true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null, stages, null, null)
    }
}
