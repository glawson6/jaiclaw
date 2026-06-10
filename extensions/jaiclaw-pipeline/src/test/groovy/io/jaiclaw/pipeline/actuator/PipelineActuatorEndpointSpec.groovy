package io.jaiclaw.pipeline.actuator

import io.jaiclaw.pipeline.OutputDefinition
import io.jaiclaw.pipeline.OutputType
import io.jaiclaw.pipeline.PipelineContext
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.PipelineRegistry
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker
import spock.lang.Specification

import java.time.Duration

class PipelineActuatorEndpointSpec extends Specification {

    PipelineRegistry registry = new PipelineRegistry()
    PipelineExecutionTracker tracker = new PipelineExecutionTracker(10)
    PipelineActuatorEndpoint endpoint = new PipelineActuatorEndpoint(registry, tracker)

    private static PipelineDefinition pipe(String id) {
        return new PipelineDefinition(id, id + " name", null, List.of(), true,
                null, null, 0, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "b", null, null, null, null, null, null)],
                new OutputDefinition(OutputType.LOG, null, null, null), null)
    }

    private static PipelineContext ctx(String executionId, String pipelineId) {
        return new PipelineContext(pipelineId, executionId, "tenant", "corr",
                0, 1, null, null, Map.of(), Map.of())
    }

    def "list returns count and pipeline summaries"() {
        given:
        registry.register(pipe("a"))
        registry.register(pipe("b"))

        when:
        Map<String, Object> result = endpoint.list()

        then:
        result.get("count") == 2
        List<Map<String, Object>> pipelines = result.get("pipelines") as List
        pipelines*.get("id").toSorted() == ["a", "b"]
        pipelines[0].get("stageNames") == ["s1"]
    }

    def "byId returns definition + recentExecutions"() {
        given:
        registry.register(pipe("a"))
        tracker.started(ctx("e1", "a"))
        tracker.succeeded(ctx("e1", "a"), Duration.ofMillis(123))

        when:
        Map<String, Object> result = endpoint.byId("a")

        then:
        Map definition = result.get("definition") as Map
        definition.get("id") == "a"
        List<Map> recent = result.get("recentExecutions") as List
        recent.size() == 1
        recent[0].get("executionId") == "e1"
        recent[0].get("status") == "SUCCESS"
        recent[0].get("totalDurationMs") == 123L
    }

    def "byId returns error for unknown pipeline"() {
        expect:
        endpoint.byId("nope").get("error") == "Pipeline 'nope' not found"
    }

    def "executionById returns full detail"() {
        given:
        registry.register(pipe("a"))
        tracker.started(ctx("e1", "a"))
        tracker.stageCompleted(ctx("e1", "a"), "s1", Duration.ofMillis(50))
        tracker.succeeded(ctx("e1", "a"), Duration.ofMillis(60))

        when:
        Map<String, Object> result = endpoint.executionById("a", "e1")

        then:
        result.get("executionId") == "e1"
        result.get("status") == "SUCCESS"
        (result.get("stageDurationsMs") as Map).get("s1") == 50L
    }

    def "executionById returns error for unknown executionId"() {
        given:
        registry.register(pipe("a"))

        expect:
        endpoint.executionById("a", "missing").get("error") ==
                "Execution 'missing' not found for pipeline 'a'"
    }

    def "executionById flags mismatched pipelineId"() {
        given:
        registry.register(pipe("a"))
        registry.register(pipe("b"))
        tracker.started(ctx("e1", "b"))

        expect:
        endpoint.executionById("a", "e1").get("error") ==
                "Execution 'e1' not found for pipeline 'a'"
    }
}
