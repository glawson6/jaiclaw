package io.jaiclaw.pipeline

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.AuditLogger
import io.jaiclaw.audit.TrajectoryRecorder
import spock.lang.Specification

import java.time.Duration

class PipelineAuditorSpec extends Specification {

    AuditLogger auditLogger = Mock()
    TrajectoryRecorder trajectoryRecorder = Mock()

    PipelineAuditor auditor = new PipelineAuditor(auditLogger, trajectoryRecorder)

    PipelineContext ctx = new PipelineContext(
            "test-pipeline", "exec-1", "tenant-1", null,
            0, 3, null, null, Map.of(), Map.of()
    )

    StageDefinition stage = new StageDefinition(
            "research", StageType.AGENT, null, "researcher",
            null, null, null, null, null
    )

    def "pipelineStarted emits audit event with correct action"() {
        when:
        auditor.pipelineStarted(ctx)

        then:
        1 * auditLogger.log({ AuditEvent event ->
            event.action() == "pipeline.started" &&
            event.resource() == "test-pipeline" &&
            event.tenantId() == "tenant-1" &&
            event.outcome() == AuditEvent.Outcome.SUCCESS &&
            event.details().get("executionId") == "exec-1" &&
            event.details().get("totalStages") == 3
        })

        and:
        1 * trajectoryRecorder.beginSession("exec-1", "tenant-1")
    }

    def "stageCompleted emits event with duration"() {
        given:
        PipelineContext ctxWithOutput = ctx.nextStage("research", "some output")

        when:
        auditor.stageCompleted(ctxWithOutput, stage, Duration.ofMillis(500))

        then:
        1 * auditLogger.log({ AuditEvent event ->
            event.action() == "pipeline.stage.completed" &&
            event.details().get("durationMs") == 500L &&
            event.details().get("stageName") == "research"
        })
    }

    def "stageFailed emits event with FAILURE outcome"() {
        when:
        auditor.stageFailed(ctx, stage, new RuntimeException("something broke"))

        then:
        1 * auditLogger.log({ AuditEvent event ->
            event.action() == "pipeline.stage.failed" &&
            event.outcome() == AuditEvent.Outcome.FAILURE &&
            event.details().get("error") == "something broke"
        })
    }

    def "pipelineCompleted emits event and ends trajectory session"() {
        when:
        auditor.pipelineCompleted(ctx, Duration.ofSeconds(10))

        then:
        1 * auditLogger.log({ AuditEvent event ->
            event.action() == "pipeline.completed" &&
            event.details().get("totalDurationMs") == 10000L
        })
        1 * trajectoryRecorder.endSession("exec-1")
    }

    def "pipelineFailed emits event with FAILURE outcome"() {
        when:
        auditor.pipelineFailed(ctx, new RuntimeException("pipeline error"))

        then:
        1 * auditLogger.log({ AuditEvent event ->
            event.action() == "pipeline.failed" &&
            event.outcome() == AuditEvent.Outcome.FAILURE
        })
        1 * trajectoryRecorder.endSession("exec-1")
    }

    def "null AuditLogger: all methods are no-ops"() {
        given:
        PipelineAuditor nullAuditor = new PipelineAuditor(null, null)

        when:
        nullAuditor.pipelineStarted(ctx)
        nullAuditor.stageStarted(ctx, stage)
        nullAuditor.stageCompleted(ctx, stage, Duration.ofMillis(100))
        nullAuditor.stageFailed(ctx, stage, new RuntimeException("err"))
        nullAuditor.pipelineCompleted(ctx, Duration.ofSeconds(5))
        nullAuditor.pipelineFailed(ctx, new RuntimeException("err"))

        then:
        noExceptionThrown()
    }

    def "tenant ID is passed through to every AuditEvent"() {
        when:
        auditor.stageStarted(ctx, stage)

        then:
        1 * auditLogger.log({ AuditEvent event ->
            event.tenantId() == "tenant-1"
        })
    }
}
