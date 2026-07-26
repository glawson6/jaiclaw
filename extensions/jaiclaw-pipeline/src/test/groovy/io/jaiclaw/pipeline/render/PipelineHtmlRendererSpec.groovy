package io.jaiclaw.pipeline.render

import io.jaiclaw.pipeline.ErrorStrategy
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.pipeline.TriggerDefinition
import io.jaiclaw.pipeline.TriggerType
import io.jaiclaw.pipeline.tracking.ExecutionStatus
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

/**
 * Enforces the HTML class-name + data-attribute contract from the plan.
 * jsoup parses every rendered snippet as a fragment; every documented
 * class name and data attribute must be present.
 */
class PipelineHtmlRendererSpec extends Specification {

    PipelineHtmlRenderer renderer = new PipelineHtmlRenderer()

    def "no <style>, <script>, <link> tags anywhere in any view"() {
        given:
        def vm = vmSuccessful()

        expect:
        for (String html : [
                renderer.renderCompact(vm),
                renderer.renderTable(vm),
                renderer.renderFlow(vm, PipelineHtmlRenderer.FlowFormat.DIV),
                renderer.renderFlow(vm, PipelineHtmlRenderer.FlowFormat.SVG)]) {
            assert !html.contains("<script")
            assert !html.contains("<style")
            assert !html.contains("<link")
        }
    }

    def "compact view — root + stage class + data attribute contract"() {
        given:
        Document doc = fragment(renderer.renderCompact(vmSuccessful()))

        expect:
        doc.selectFirst(".jaiclaw-pipeline.jaiclaw-pipeline--compact") != null
        doc.selectFirst(".jaiclaw-pipeline--status-success") != null
        doc.selectFirst(".jaiclaw-pipeline-header") != null
        doc.selectFirst(".jaiclaw-pipeline-stage-list") != null
        doc.select(".jaiclaw-pipeline-stage").size() == 3
        doc.select(".jaiclaw-pipeline-stage--done").size() == 3
        doc.select(".jaiclaw-pipeline-stage--processor").size() == 3
        // Data attributes on stage items
        def firstStage = doc.selectFirst(".jaiclaw-pipeline-stage")
        firstStage.attr("data-stage") == "fetch"
        firstStage.attr("data-stage-status") == "done"
        firstStage.attr("data-stage-type") == "processor"
        firstStage.attr("data-stage-index") == "0"
        firstStage.attr("data-duration-ms") == "3400"
        // Data attributes on root
        def root = doc.selectFirst(".jaiclaw-pipeline")
        root.attr("data-pipeline-id") == "nightly-price-sync"
        root.attr("data-execution-id").startsWith("8a9c2f")
        root.attr("data-view") == "compact"
        root.attr("data-status") == "success"
    }

    def "table view — proper table markup + cell classes"() {
        given:
        Document doc = fragment(renderer.renderTable(vmSuccessful()))

        expect:
        doc.selectFirst(".jaiclaw-pipeline--table") != null
        doc.selectFirst(".jaiclaw-pipeline-table") != null
        doc.selectFirst(".jaiclaw-pipeline-table-head") != null
        doc.selectFirst(".jaiclaw-pipeline-table-body") != null
        doc.select(".jaiclaw-pipeline-table-row").size() == 3
        doc.select(".jaiclaw-pipeline-table-row--done").size() == 3
        // Cell classes per column
        doc.select(".jaiclaw-pipeline-table-cell--stage").size() >= 3     // includes header
        doc.select(".jaiclaw-pipeline-table-cell--type").size() >= 3
        doc.select(".jaiclaw-pipeline-table-cell--status").size() >= 3
        doc.select(".jaiclaw-pipeline-table-cell--duration").size() >= 3
        doc.select(".jaiclaw-pipeline-table-cell--output").size() >= 3
        // data-* on rows
        def firstRow = doc.selectFirst(".jaiclaw-pipeline-table-row")
        firstRow.attr("data-stage") == "fetch"
        firstRow.attr("data-stage-status") == "done"
    }

    def "flow view (div format) — nested divs + connector arrows"() {
        given:
        Document doc = fragment(renderer.renderFlow(vmSuccessful(), PipelineHtmlRenderer.FlowFormat.DIV))

        expect:
        doc.selectFirst(".jaiclaw-pipeline--flow") != null
        doc.selectFirst(".jaiclaw-pipeline-flow") != null
        doc.select(".jaiclaw-pipeline-node").size() == 3
        doc.select(".jaiclaw-pipeline-node--done").size() == 3
        doc.select(".jaiclaw-pipeline-node-header").size() == 3
        doc.select(".jaiclaw-pipeline-node-body").size() == 3
        // 3 stages -> 2 connectors between them
        doc.select(".jaiclaw-pipeline-connector").size() == 2
    }

    def "flow view (svg format) — svg root + node/connector classes"() {
        given:
        Document doc = fragment(renderer.renderFlow(vmSuccessful(), PipelineHtmlRenderer.FlowFormat.SVG))

        expect:
        doc.selectFirst(".jaiclaw-pipeline--flow-svg") != null
        doc.selectFirst(".jaiclaw-pipeline-svg") != null
        doc.select(".jaiclaw-pipeline-svg-node").size() == 3
        doc.select(".jaiclaw-pipeline-svg-node--done").size() == 3
        doc.select(".jaiclaw-pipeline-svg-node-rect").size() == 3
        doc.select(".jaiclaw-pipeline-svg-connector").size() >= 2   // line + polygon per gap
    }

    def "running stage gets --running modifier in the flow view"() {
        given:
        Document doc = fragment(renderer.renderFlow(vmRunning(), PipelineHtmlRenderer.FlowFormat.DIV))

        expect:
        doc.select(".jaiclaw-pipeline-node--done").size() == 1
        doc.select(".jaiclaw-pipeline-node--running").size() == 1
        doc.select(".jaiclaw-pipeline-node--pending").size() == 1
    }

    def "failed stage carries --failed modifier and includes failure reason"() {
        given:
        Document doc = fragment(renderer.renderCompact(vmFailed()))

        expect:
        doc.selectFirst(".jaiclaw-pipeline--status-failed") != null
        doc.selectFirst(".jaiclaw-pipeline-stage--failed") != null
    }

    def "html escaping — angle brackets in stage name / failure reason become entities"() {
        given:
        def def_ = def_("weird<>pipe", ["<script>alert(1)</script>"])
        def summary = new PipelineExecutionSummary(
                "exec-x", "weird<>pipe", null,
                Instant.now(), null,
                ExecutionStatus.FAILED, "<script>alert(1)</script>",
                [:], "Fault at <line 42>", null)
        def vm = PipelineViewModel.of(def_, summary)

        when:
        String html = renderer.renderCompact(vm)

        then:
        !html.contains("<script>alert(1)</script>")   // must not survive escaping
        html.contains("&lt;script&gt;")
        html.contains("Fault at &lt;line 42&gt;")
    }

    // ── fixtures ───────────────────────────────────────────

    private static Document fragment(String html) {
        return Jsoup.parse(html, "", Parser.htmlParser())
    }

    private PipelineViewModel vmSuccessful() {
        def def_ = def_("nightly-price-sync", ["fetch", "normalize", "persist"])
        def summary = new PipelineExecutionSummary(
                "8a9c2f1234abcd", "nightly-price-sync", null,
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

    private PipelineViewModel vmFailed() {
        def def_ = def_("p", ["fetch", "normalize"])
        def summary = new PipelineExecutionSummary(
                "exec-1", "p", null,
                Instant.now(), null,
                ExecutionStatus.FAILED, "normalize",
                ["fetch": Duration.ofMillis(1000)],
                "MalformedPriceRow at line 42",
                Duration.ofMillis(1100))
        return PipelineViewModel.of(def_, summary)
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
