package io.jaiclaw.pipeline

import spock.lang.Specification

import java.time.Instant

/**
 * Unit tests for {@link PipelineRouteBuilder#resolveResult(PipelineContext, PipelineDefinition)}.
 * Covers the six cases the issue at
 * {@code docs/issues/pipeline-author-result-slot.md} calls out.
 */
class PipelineResultResolutionSpec extends Specification {

    private static PipelineContext ctxWith(Map<String, PipelineContext.StageOutput> outputs) {
        return new PipelineContext("pipe-1", "exec-1", null, "corr-1",
                outputs.size(), outputs.size(),
                null, null,
                outputs, [:] as Map<String, String>)
    }

    private static PipelineContext.StageOutput out(String body, Map<String, String> metadata) {
        return new PipelineContext.StageOutput(body, metadata, Instant.now())
    }

    private static PipelineDefinition definitionWith(String resultTemplate) {
        return new PipelineDefinition(
                "pipe-1", "pipe", null, [] as List<String>, true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null,
                [] as List<StageDefinition>,
                new OutputDefinition(OutputType.NONE, null, null, null),
                PipelineSecurityProperties.DEFAULT,
                resultTemplate)
    }

    def "stage-written RESULT_KEY on the last stage is returned"() {
        given:
        def outputs = new LinkedHashMap<String, PipelineContext.StageOutput>()
        outputs.put("first", out("first-output", [:]))
        outputs.put("last", out("last-output",
                [(PipelineResult.RESULT_KEY): "hello from last stage"]))
        PipelineContext ctx = ctxWith(outputs)
        PipelineDefinition definition = definitionWith(null)

        expect:
        PipelineRouteBuilder.resolveResult(ctx, definition) == "hello from last stage"
    }

    def "walks in reverse insertion order — first non-blank RESULT_KEY wins"() {
        given:
        def outputs = new LinkedHashMap<String, PipelineContext.StageOutput>()
        outputs.put("early", out("e", [(PipelineResult.RESULT_KEY): "from early stage"]))
        outputs.put("middle", out("m", [:]))
        outputs.put("last", out("l", [:]))
        PipelineContext ctx = ctxWith(outputs)

        expect: "walks last→middle→early, finds it on early"
        PipelineRouteBuilder.resolveResult(ctx, definitionWith(null)) == "from early stage"
    }

    def "no stage RESULT_KEY, no template → fallback SUCCESS"() {
        given:
        def outputs = new LinkedHashMap<String, PipelineContext.StageOutput>()
        outputs.put("only", out("some-output", [:]))
        PipelineContext ctx = ctxWith(outputs)

        expect:
        PipelineRouteBuilder.resolveResult(ctx, definitionWith(null)) == "SUCCESS"
    }

    def "resultTemplate alone resolves through TemplateResolver"() {
        given:
        def outputs = new LinkedHashMap<String, PipelineContext.StageOutput>()
        outputs.put("upper", out("HELLO", [:]))
        PipelineContext ctx = ctxWith(outputs)
        PipelineDefinition definition = definitionWith("processed: {{stages.upper.output}}")

        expect:
        PipelineRouteBuilder.resolveResult(ctx, definition) == "processed: HELLO"
    }

    def "stage RESULT_KEY takes precedence over resultTemplate"() {
        given:
        def outputs = new LinkedHashMap<String, PipelineContext.StageOutput>()
        outputs.put("last", out("HELLO",
                [(PipelineResult.RESULT_KEY): "stage-wins"]))
        PipelineContext ctx = ctxWith(outputs)
        PipelineDefinition definition = definitionWith("template: {{stages.last.output}}")

        expect:
        PipelineRouteBuilder.resolveResult(ctx, definition) == "stage-wins"
    }

    def "blank stage RESULT_KEY falls through to the template"() {
        given:
        def outputs = new LinkedHashMap<String, PipelineContext.StageOutput>()
        outputs.put("last", out("HELLO", [(PipelineResult.RESULT_KEY): "   "]))
        PipelineContext ctx = ctxWith(outputs)
        PipelineDefinition definition = definitionWith("processed: {{stages.last.output}}")

        expect:
        PipelineRouteBuilder.resolveResult(ctx, definition) == "processed: HELLO"
    }

    def "null context returns fallback SUCCESS"() {
        expect:
        PipelineRouteBuilder.resolveResult(null, definitionWith(null)) == "SUCCESS"
    }

    def "null definition + no stage writes returns fallback SUCCESS"() {
        given:
        def outputs = new LinkedHashMap<String, PipelineContext.StageOutput>()
        outputs.put("only", out("x", [:]))
        PipelineContext ctx = ctxWith(outputs)

        expect:
        PipelineRouteBuilder.resolveResult(ctx, null) == "SUCCESS"
    }
}
