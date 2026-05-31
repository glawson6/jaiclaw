package io.jaiclaw.audit

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class TrajectoryStepSpec extends Specification {

    def "builder creates step with all fields"() {
        given:
        Instant now = Instant.now()

        when:
        TrajectoryStep step = TrajectoryStep.builder()
                .stepIndex(0)
                .stepType(TrajectoryStep.StepType.LLM_CALL)
                .toolName(null)
                .inputSummary("Hello")
                .outputSummary("Hi there")
                .tokenCount(150)
                .duration(Duration.ofMillis(500))
                .timestamp(now)
                .traceId("trace-abc")
                .metadata(Map.of("model", "gpt-4"))
                .build()

        then:
        step.stepIndex() == 0
        step.stepType() == TrajectoryStep.StepType.LLM_CALL
        step.toolName() == null
        step.inputSummary() == "Hello"
        step.outputSummary() == "Hi there"
        step.tokenCount() == 150
        step.duration() == Duration.ofMillis(500)
        step.timestamp() == now
        step.traceId() == "trace-abc"
        step.metadata() == Map.of("model", "gpt-4")
    }

    def "defaults handle nulls"() {
        when:
        TrajectoryStep step = new TrajectoryStep(0, null, null, null, null, 0, null, null, null, null)

        then:
        step.stepType() == TrajectoryStep.StepType.SYSTEM
        step.timestamp() != null
        step.duration() == Duration.ZERO
        step.metadata() == Map.of()
        step.inputSummary() == ""
        step.outputSummary() == ""
    }

    def "toAuditEvent creates correct event for tool call"() {
        given:
        TrajectoryStep step = TrajectoryStep.builder()
                .stepIndex(3)
                .stepType(TrajectoryStep.StepType.TOOL_CALL)
                .toolName("web_search")
                .inputSummary("query=test")
                .outputSummary("5 results")
                .tokenCount(0)
                .duration(Duration.ofMillis(200))
                .traceId("trace-123")
                .build()

        when:
        AuditEvent event = step.toAuditEvent("tenant-1", "session-abc")

        then:
        event.id() == "traj-session-abc-3"
        event.tenantId() == "tenant-1"
        event.actor() == "agent"
        event.action() == "trajectory.tool_call"
        event.resource() == "web_search"
        event.outcome() == AuditEvent.Outcome.SUCCESS
        event.details().get("stepIndex") == 3
        event.details().get("stepType") == "TOOL_CALL"
        event.details().get("durationMs") == 200L
        event.details().get("traceId") == "trace-123"
    }

    def "toAuditEvent uses session key as resource when no tool name"() {
        given:
        TrajectoryStep step = TrajectoryStep.builder()
                .stepIndex(0)
                .stepType(TrajectoryStep.StepType.LLM_CALL)
                .duration(Duration.ofMillis(100))
                .build()

        when:
        AuditEvent event = step.toAuditEvent(null, "session-xyz")

        then:
        event.resource() == "session-xyz"
        event.action() == "trajectory.llm_call"
    }

    def "all StepType values exist"() {
        expect:
        TrajectoryStep.StepType.values().length == 6
        TrajectoryStep.StepType.LLM_CALL != null
        TrajectoryStep.StepType.TOOL_CALL != null
        TrajectoryStep.StepType.COMPACTION != null
        TrajectoryStep.StepType.MEMORY_SEARCH != null
        TrajectoryStep.StepType.HOOK != null
        TrajectoryStep.StepType.SYSTEM != null
    }
}
