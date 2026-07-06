package io.jaiclaw.pipeline

import spock.lang.Specification

class TemplateResolverSpec extends Specification {

    private static PipelineContext ctxWith(Map<String, String> metadata = Map.of(),
                                           Map<String, PipelineContext.StageOutput> outputs = Map.of()) {
        return new PipelineContext("p1", "exec-1", "tenant-x", "corr-1",
                0, 2, null, null, outputs, metadata)
    }

    def "resolves stages.X.output placeholder"() {
        given:
        PipelineContext ctx = ctxWith([:], Map.of(
                "research", new PipelineContext.StageOutput("AI findings", Map.of(), null)
        ))

        expect:
        TemplateResolver.resolve("Use this: {{stages.research.output}}", ctx) ==
                "Use this: AI findings"
    }

    def "resolves multiple output placeholders"() {
        given:
        PipelineContext ctx = ctxWith([:], Map.of(
                "research", new PipelineContext.StageOutput("findings", Map.of(), null),
                "draft", new PipelineContext.StageOutput("article text", Map.of(), null)
        ))

        expect:
        TemplateResolver.resolve(
                "Research: {{stages.research.output}} Draft: {{stages.draft.output}}", ctx
        ) == "Research: findings Draft: article text"
    }

    def "resolves stages.X.metadata.key placeholder"() {
        given:
        PipelineContext ctx = ctxWith([:], Map.of(
                "analyze", new PipelineContext.StageOutput("output",
                        Map.of("confidence", "0.95", "model", "gpt-4"), null)
        ))

        expect:
        TemplateResolver.resolve(
                "Confidence: {{stages.analyze.metadata.confidence}}", ctx
        ) == "Confidence: 0.95"
    }

    def "unresolved placeholders are left as-is"() {
        given:
        PipelineContext ctx = ctxWith([:], Map.of(
                "research", new PipelineContext.StageOutput("findings", Map.of(), null)
        ))

        expect:
        TemplateResolver.resolve(
                "{{stages.unknown.output}} and {{stages.research.metadata.missing}}", ctx
        ) == "{{stages.unknown.output}} and {{stages.research.metadata.missing}}"
    }

    def "null template returns null"() {
        expect:
        TemplateResolver.resolve((String) null, ctxWith()) == null
    }

    def "empty template returns empty"() {
        expect:
        TemplateResolver.resolve("", ctxWith()) == ""
    }

    def "null context returns template unchanged"() {
        expect:
        TemplateResolver.resolve("{{stages.x.output}}", (PipelineContext) null) == "{{stages.x.output}}"
    }

    def "empty outputs returns template unchanged"() {
        expect:
        TemplateResolver.resolve("{{stages.x.output}}", ctxWith()) == "{{stages.x.output}}"
    }

    def "template without placeholders returns as-is"() {
        given:
        PipelineContext ctx = ctxWith([:], Map.of(
                "research", new PipelineContext.StageOutput("findings", Map.of(), null)
        ))

        expect:
        TemplateResolver.resolve("No placeholders here", ctx) == "No placeholders here"
    }

    // --- context-aware overload (Phase B) ---

    def "context overload resolves {{input}} from metadata"() {
        given:
        PipelineContext ctx = ctxWith([(PipelineContext.INPUT_METADATA_KEY): "the original payload"])

        expect:
        TemplateResolver.resolve("Topic: {{input}}", ctx) == "Topic: the original payload"
    }

    def "context overload resolves {{pipeline.*}} placeholders"() {
        given:
        PipelineContext ctx = ctxWith()

        expect:
        TemplateResolver.resolve("id={{pipeline.id}} tenant={{pipeline.tenantId}}", ctx) ==
                "id=p1 tenant=tenant-x"
    }

    def "context overload still resolves stage outputs and metadata"() {
        given:
        Map<String, PipelineContext.StageOutput> outputs = Map.of(
                "research", new PipelineContext.StageOutput("findings",
                        Map.of("confidence", "0.95"), null))
        PipelineContext ctx = ctxWith([:], outputs)

        expect:
        TemplateResolver.resolve(
                "out={{stages.research.output}} conf={{stages.research.metadata.confidence}}", ctx) ==
                "out=findings conf=0.95"
    }

    def "context overload leaves unresolved placeholders in place"() {
        given:
        PipelineContext ctx = ctxWith()

        expect:
        TemplateResolver.resolve("Look at {{stages.missing.output}}", ctx) ==
                "Look at {{stages.missing.output}}"
    }

    def "stage names with hyphens are resolved"() {
        given:
        PipelineContext ctx = ctxWith([:], Map.of(
                "classify-and-sentiment", new PipelineContext.StageOutput("intent: billing", Map.of(), null)))

        expect:
        TemplateResolver.resolve("X={{stages.classify-and-sentiment.output}}", ctx) ==
                "X=intent: billing"
    }

    def "context overload returns null/empty unchanged"() {
        given:
        PipelineContext ctx = ctxWith()

        expect:
        TemplateResolver.resolve((String) null, ctx) == null
        TemplateResolver.resolve("", ctx) == ""
    }
}
