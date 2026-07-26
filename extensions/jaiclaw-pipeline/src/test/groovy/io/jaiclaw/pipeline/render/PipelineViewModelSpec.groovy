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

class PipelineViewModelSpec extends Specification {

    def "no execution → all stages PENDING and no overall status"() {
        given:
        def def_ = definition("p1", ["fetch", "normalize", "persist"])

        when:
        def vm = PipelineViewModel.of(def_, null)

        then:
        vm.pipelineId() == "p1"
        vm.overallStatus() == null
        vm.stages().size() == 3
        vm.stages().every { it.status() == StageStatus.PENDING }
        vm.stages().every { it.durationMs() == null }
    }

    def "with summary — completed stages become DONE, current becomes RUNNING, rest PENDING"() {
        given:
        def def_ = definition("p1", ["fetch", "normalize", "persist"])
        def summary = summary("exec-1", "p1", ExecutionStatus.RUNNING,
                "normalize",
                ["fetch": Duration.ofMillis(3400)])

        when:
        def vm = PipelineViewModel.of(def_, summary)

        then:
        vm.stages()[0].status() == StageStatus.DONE
        vm.stages()[0].durationMs() == 3400L
        vm.stages()[1].status() == StageStatus.RUNNING
        vm.stages()[1].durationMs() == null
        vm.stages()[2].status() == StageStatus.PENDING
        vm.overallStatus() == ExecutionStatus.RUNNING
        vm.currentStageIndex1Based() == 2
    }

    def "FAILED overall + currentStage → that stage becomes FAILED with failureReason"() {
        given:
        def def_ = definition("p1", ["fetch", "normalize"])
        def summary = new PipelineExecutionSummary(
                "exec-1", "p1", "tenant-x",
                Instant.now(), null, ExecutionStatus.FAILED, "normalize",
                ["fetch": Duration.ofMillis(1000)],
                "MalformedPriceRow at line 42",
                Duration.ofMillis(1100))

        when:
        def vm = PipelineViewModel.of(def_, summary)

        then:
        vm.stages()[0].status() == StageStatus.DONE
        vm.stages()[1].status() == StageStatus.FAILED
        vm.stages()[1].failureReason() == "MalformedPriceRow at line 42"
        vm.overallStatus() == ExecutionStatus.FAILED
    }

    def "durationLabel formats sub-second and multi-second"() {
        expect:
        new PipelineViewModel.Stage("s", StageType.PROCESSOR, StageStatus.DONE, ms, null, null, "", 0)
                .durationLabel() == label

        where:
        ms      || label
        null    || "—"
        42L     || "42ms"
        1000L   || "1.0s"
        3400L   || "3.4s"
        8749L   || "8.7s"
    }

    def "beanOrUri prefers bean over uri over agentId"() {
        expect:
        stageBeanOrUri(new StageDefinition("s", StageType.PROCESSOR, "beanA", "agentX", null, null, "camel:x", null, null, null, null)) == "bean:beanA"
        stageBeanOrUri(new StageDefinition("s", StageType.CAMEL, null, null, null, null, "camel:https://x", null, null, null, null)) == "camel:https://x"
        stageBeanOrUri(new StageDefinition("s", StageType.AGENT, null, "agentZ", null, null, null, null, null, null, null)) == "agent:agentZ"
        stageBeanOrUri(new StageDefinition("s", StageType.PROCESSOR, null, null, null, null, null, null, null, null, null)) == ""
    }

    private String stageBeanOrUri(StageDefinition sd) {
        def def_ = new PipelineDefinition("p", "P", "d", [], true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null, [sd], null, null)
        return PipelineViewModel.of(def_, null).stages()[0].beanOrUri()
    }

    def "shortExecutionId returns first 6 chars"() {
        expect:
        vm("abcdef123456").shortExecutionId() == "abcdef"
        vm("ab").shortExecutionId() == "ab"
        vm(null).shortExecutionId() == ""
    }

    private PipelineViewModel vm(String execId) {
        def def_ = definition("p", ["s1"])
        def summary = execId == null ? null : new PipelineExecutionSummary(
                execId, "p", null,
                Instant.now(), Instant.now(),
                ExecutionStatus.SUCCESS, null, [:], null, Duration.ofMillis(1))
        return PipelineViewModel.of(def_, summary)
    }

    private PipelineDefinition definition(String id, List<String> stageNames) {
        def stages = stageNames.collect { name ->
            new StageDefinition(name, StageType.PROCESSOR, "bean-" + name,
                    null, null, null, null, null, null, null, null)
        }
        return new PipelineDefinition(id, id + " display", null, [], true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null, stages, null, null)
    }

    private PipelineExecutionSummary summary(String executionId, String pipelineId,
                                             ExecutionStatus status, String currentStage,
                                             Map<String, Duration> stageDurations) {
        return new PipelineExecutionSummary(
                executionId, pipelineId, "tenant-x",
                Instant.now().minusSeconds(5), null,
                status, currentStage, stageDurations, null, null)
    }
}
