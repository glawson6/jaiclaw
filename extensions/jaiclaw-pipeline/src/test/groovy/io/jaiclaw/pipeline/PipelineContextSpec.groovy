package io.jaiclaw.pipeline

import io.jaiclaw.camel.PipelineEnvelope
import spock.lang.Specification

class PipelineContextSpec extends Specification {

    def "nextStage advances index and adds output"() {
        given:
        PipelineContext ctx = new PipelineContext(
                "test-pipeline", "exec-1", "tenant-1", "corr-1",
                0, 3, null, null, Map.of(), Map.of()
        )

        when:
        PipelineContext next = ctx.nextStage("research", "Research results here")

        then:
        next.stageIndex() == 1
        next.stageOutputs().containsKey("research")
        next.stageOutputs().get("research").output() == "Research results here"
        next.pipelineId() == "test-pipeline"
        next.executionId() == "exec-1"
    }

    def "nextStage accumulates multiple outputs"() {
        given:
        PipelineContext ctx = new PipelineContext(
                "test-pipeline", "exec-1", null, null,
                0, 3, null, null, Map.of(), Map.of()
        )

        when:
        PipelineContext after1 = ctx.nextStage("stage-0", "output-0")
        PipelineContext after2 = after1.nextStage("stage-1", "output-1")

        then:
        after2.stageIndex() == 2
        after2.stageOutputs().size() == 2
        after2.stageOutputs().get("stage-0").output() == "output-0"
        after2.stageOutputs().get("stage-1").output() == "output-1"
    }

    def "isLastStage returns true at boundary"() {
        given:
        PipelineContext ctx = new PipelineContext(
                "p", "e", null, null,
                2, 3, null, null, Map.of(), Map.of()
        )

        expect:
        ctx.isLastStage()
    }

    def "isLastStage returns false before boundary"() {
        given:
        PipelineContext ctx = new PipelineContext(
                "p", "e", null, null,
                1, 3, null, null, Map.of(), Map.of()
        )

        expect:
        !ctx.isLastStage()
    }

    def "isLastStage returns true when stageIndex exceeds totalStages"() {
        given:
        PipelineContext ctx = new PipelineContext(
                "p", "e", null, null,
                5, 3, null, null, Map.of(), Map.of()
        )

        expect:
        ctx.isLastStage()
    }

    def "currentStageName returns correct name"() {
        given:
        List<StageDefinition> stages = [
                new StageDefinition("alpha", StageType.PROCESSOR, null, null, null, null, null, null, null),
                new StageDefinition("beta", StageType.AGENT, null, "agent-1", null, null, null, null, null),
                new StageDefinition("gamma", StageType.CAMEL, null, null, null, null, "log:test", null, null),
        ]
        PipelineContext ctx = new PipelineContext(
                "p", "e", null, null,
                1, 3, null, null, Map.of(), Map.of()
        )

        expect:
        ctx.currentStageName(stages) == "beta"
    }

    def "currentStageName returns unknown for out of bounds"() {
        given:
        List<StageDefinition> stages = [
                new StageDefinition("alpha", StageType.PROCESSOR, null, null, null, null, null, null, null)
        ]
        PipelineContext ctx = new PipelineContext(
                "p", "e", null, null,
                5, 3, null, null, Map.of(), Map.of()
        )

        expect:
        ctx.currentStageName(stages) == "unknown"
    }

    def "fromEnvelope bridges PipelineEnvelope correctly"() {
        given:
        PipelineEnvelope envelope = new PipelineEnvelope(
                "pipe-1", "corr-123", 1, 3, "slack", "user-1",
                ["output-0"]
        )

        when:
        PipelineContext ctx = PipelineContext.fromEnvelope(envelope)

        then:
        ctx.pipelineId() == "pipe-1"
        ctx.correlationId() == "corr-123"
        ctx.stageIndex() == 1
        ctx.totalStages() == 3
        ctx.replyChannelId() == "slack"
        ctx.replyPeerId() == "user-1"
        ctx.stageOutputs().size() == 1
        ctx.stageOutputs().get("stage-0").output() == "output-0"
    }

    def "defensive copying makes stageOutputs immutable"() {
        given:
        Map<String, PipelineContext.StageOutput> outputs = new HashMap<>()
        outputs.put("s1", new PipelineContext.StageOutput("out", Map.of(), null))
        PipelineContext ctx = new PipelineContext(
                "p", "e", null, null,
                0, 1, null, null, outputs, Map.of()
        )

        when:
        outputs.put("s2", new PipelineContext.StageOutput("out2", Map.of(), null))

        then:
        ctx.stageOutputs().size() == 1
    }

    def "defensive copying makes metadata immutable"() {
        given:
        Map<String, String> meta = new HashMap<>()
        meta.put("k1", "v1")
        PipelineContext ctx = new PipelineContext(
                "p", "e", null, null,
                0, 1, null, null, Map.of(), meta
        )

        when:
        meta.put("k2", "v2")

        then:
        ctx.metadata().size() == 1
    }

    def "null stageOutputs defaults to empty map"() {
        given:
        PipelineContext ctx = new PipelineContext(
                "p", "e", null, null,
                0, 1, null, null, null, null
        )

        expect:
        ctx.stageOutputs().isEmpty()
        ctx.metadata().isEmpty()
    }

    def "blank pipelineId throws IllegalArgumentException"() {
        when:
        new PipelineContext("", "e", null, null, 0, 1, null, null, null, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "null executionId generates UUID"() {
        given:
        PipelineContext ctx = new PipelineContext(
                "p", null, null, null,
                0, 1, null, null, null, null
        )

        expect:
        ctx.executionId() != null
        !ctx.executionId().isBlank()
    }
}
