package io.jaiclaw.audit

import spock.lang.Specification

import java.time.Duration

class TrajectoryRecorderSpec extends Specification {

    def "beginSession and endSession lifecycle"() {
        given:
        AuditLogger mockLogger = Mock(AuditLogger)
        TrajectoryRecorder recorder = new TrajectoryRecorder(mockLogger)

        when:
        recorder.beginSession("session-1", "tenant-a")

        then:
        recorder.isRecording("session-1")
        recorder.getSteps("session-1").isEmpty()

        when:
        List<TrajectoryStep> steps = recorder.endSession("session-1")

        then:
        !recorder.isRecording("session-1")
        steps.isEmpty()
    }

    def "recordLlmCall emits step and audit event"() {
        given:
        AuditLogger mockLogger = Mock(AuditLogger)
        TrajectoryRecorder recorder = new TrajectoryRecorder(mockLogger)
        recorder.beginSession("session-1", "tenant-a")

        when:
        recorder.recordLlmCall("session-1", "tenant-a", "Hello", "Hi there", 150, Duration.ofMillis(500))

        then:
        1 * mockLogger.log({ AuditEvent e ->
            e.action() == "trajectory.llm_call" &&
            e.details().get("tokenCount") == 150
        })

        and:
        List<TrajectoryStep> steps = recorder.getSteps("session-1")
        steps.size() == 1
        steps[0].stepType() == TrajectoryStep.StepType.LLM_CALL
        steps[0].inputSummary() == "Hello"
        steps[0].outputSummary() == "Hi there"
        steps[0].tokenCount() == 150
    }

    def "recordToolCall emits step with tool name"() {
        given:
        AuditLogger mockLogger = Mock(AuditLogger)
        TrajectoryRecorder recorder = new TrajectoryRecorder(mockLogger)
        recorder.beginSession("session-1", "tenant-a")

        when:
        recorder.recordToolCall("session-1", "tenant-a", "web_search", "query", "5 results", Duration.ofMillis(200))

        then:
        1 * mockLogger.log({ AuditEvent e ->
            e.action() == "trajectory.tool_call" &&
            e.resource() == "web_search"
        })

        and:
        List<TrajectoryStep> steps = recorder.getSteps("session-1")
        steps[0].toolName() == "web_search"
    }

    def "recordCompaction emits step with token counts"() {
        given:
        AuditLogger mockLogger = Mock(AuditLogger)
        TrajectoryRecorder recorder = new TrajectoryRecorder(mockLogger)
        recorder.beginSession("session-1", "tenant-a")

        when:
        recorder.recordCompaction("session-1", "tenant-a", 10000, 5000, Duration.ofMillis(300))

        then:
        1 * mockLogger.log({ AuditEvent e ->
            e.action() == "trajectory.compaction"
        })

        and:
        List<TrajectoryStep> steps = recorder.getSteps("session-1")
        steps[0].stepType() == TrajectoryStep.StepType.COMPACTION
        steps[0].metadata().get("tokensBefore") == 10000
        steps[0].metadata().get("tokensAfter") == 5000
    }

    def "recordMemorySearch emits step with query and result count"() {
        given:
        AuditLogger mockLogger = Mock(AuditLogger)
        TrajectoryRecorder recorder = new TrajectoryRecorder(mockLogger)
        recorder.beginSession("session-1", "tenant-a")

        when:
        recorder.recordMemorySearch("session-1", "tenant-a", "how to deploy", 3, Duration.ofMillis(50))

        then:
        1 * mockLogger.log(_)

        and:
        List<TrajectoryStep> steps = recorder.getSteps("session-1")
        steps[0].stepType() == TrajectoryStep.StepType.MEMORY_SEARCH
        steps[0].inputSummary() == "how to deploy"
        steps[0].outputSummary() == "3 results"
    }

    def "multiple steps have incrementing step indices"() {
        given:
        AuditLogger mockLogger = Mock(AuditLogger)
        TrajectoryRecorder recorder = new TrajectoryRecorder(mockLogger)
        recorder.beginSession("session-1", "tenant-a")

        when:
        recorder.recordLlmCall("session-1", "tenant-a", "q1", "a1", 100, Duration.ofMillis(100))
        recorder.recordToolCall("session-1", "tenant-a", "tool1", "in", "out", Duration.ofMillis(50))
        recorder.recordLlmCall("session-1", "tenant-a", "q2", "a2", 200, Duration.ofMillis(150))

        then:
        List<TrajectoryStep> steps = recorder.getSteps("session-1")
        steps.size() == 3
        steps[0].stepIndex() == 0
        steps[1].stepIndex() == 1
        steps[2].stepIndex() == 2
    }

    def "endSession returns steps and clears state"() {
        given:
        AuditLogger mockLogger = Mock(AuditLogger)
        TrajectoryRecorder recorder = new TrajectoryRecorder(mockLogger)
        recorder.beginSession("session-1", "tenant-a")
        recorder.recordLlmCall("session-1", "tenant-a", "q", "a", 50, Duration.ofMillis(100))

        when:
        List<TrajectoryStep> steps = recorder.endSession("session-1")

        then:
        steps.size() == 1
        !recorder.isRecording("session-1")
        recorder.getSteps("session-1").isEmpty()
    }

    def "endSession for unknown session returns empty list"() {
        given:
        AuditLogger mockLogger = Mock(AuditLogger)
        TrajectoryRecorder recorder = new TrajectoryRecorder(mockLogger)

        when:
        List<TrajectoryStep> steps = recorder.endSession("nonexistent")

        then:
        steps.isEmpty()
    }

    def "input summary is truncated at 500 chars"() {
        given:
        AuditLogger mockLogger = Mock(AuditLogger)
        TrajectoryRecorder recorder = new TrajectoryRecorder(mockLogger)
        recorder.beginSession("session-1", "tenant-a")
        String longInput = "x" * 600

        when:
        recorder.recordLlmCall("session-1", "tenant-a", longInput, "short", 100, Duration.ofMillis(100))

        then:
        List<TrajectoryStep> steps = recorder.getSteps("session-1")
        steps[0].inputSummary().length() == 503  // 500 + "..."
        steps[0].inputSummary().endsWith("...")
    }

    def "isRecording returns false for unknown session"() {
        given:
        AuditLogger mockLogger = Mock(AuditLogger)
        TrajectoryRecorder recorder = new TrajectoryRecorder(mockLogger)

        expect:
        !recorder.isRecording("unknown")
    }
}
