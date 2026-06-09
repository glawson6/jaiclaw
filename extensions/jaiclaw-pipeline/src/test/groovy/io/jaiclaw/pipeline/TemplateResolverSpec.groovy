package io.jaiclaw.pipeline

import spock.lang.Specification

class TemplateResolverSpec extends Specification {

    def "resolves stages.X.output placeholder"() {
        given:
        Map<String, PipelineContext.StageOutput> outputs = Map.of(
                "research", new PipelineContext.StageOutput("AI findings", Map.of(), null)
        )

        expect:
        TemplateResolver.resolve("Use this: {{stages.research.output}}", outputs) ==
                "Use this: AI findings"
    }

    def "resolves multiple output placeholders"() {
        given:
        Map<String, PipelineContext.StageOutput> outputs = Map.of(
                "research", new PipelineContext.StageOutput("findings", Map.of(), null),
                "draft", new PipelineContext.StageOutput("article text", Map.of(), null)
        )

        expect:
        TemplateResolver.resolve(
                "Research: {{stages.research.output}} Draft: {{stages.draft.output}}", outputs
        ) == "Research: findings Draft: article text"
    }

    def "resolves stages.X.metadata.key placeholder"() {
        given:
        Map<String, PipelineContext.StageOutput> outputs = Map.of(
                "analyze", new PipelineContext.StageOutput("output",
                        Map.of("confidence", "0.95", "model", "gpt-4"), null)
        )

        expect:
        TemplateResolver.resolve(
                "Confidence: {{stages.analyze.metadata.confidence}}", outputs
        ) == "Confidence: 0.95"
    }

    def "unresolved placeholders are left as-is"() {
        given:
        Map<String, PipelineContext.StageOutput> outputs = Map.of(
                "research", new PipelineContext.StageOutput("findings", Map.of(), null)
        )

        expect:
        TemplateResolver.resolve(
                "{{stages.unknown.output}} and {{stages.research.metadata.missing}}", outputs
        ) == "{{stages.unknown.output}} and {{stages.research.metadata.missing}}"
    }

    def "null template returns null"() {
        expect:
        TemplateResolver.resolve(null, Map.of()) == null
    }

    def "empty template returns empty"() {
        expect:
        TemplateResolver.resolve("", Map.of()) == ""
    }

    def "null outputs returns template unchanged"() {
        expect:
        TemplateResolver.resolve("{{stages.x.output}}", null) == "{{stages.x.output}}"
    }

    def "empty outputs returns template unchanged"() {
        expect:
        TemplateResolver.resolve("{{stages.x.output}}", Map.of()) == "{{stages.x.output}}"
    }

    def "template without placeholders returns as-is"() {
        given:
        Map<String, PipelineContext.StageOutput> outputs = Map.of(
                "research", new PipelineContext.StageOutput("findings", Map.of(), null)
        )

        expect:
        TemplateResolver.resolve("No placeholders here", outputs) == "No placeholders here"
    }
}
