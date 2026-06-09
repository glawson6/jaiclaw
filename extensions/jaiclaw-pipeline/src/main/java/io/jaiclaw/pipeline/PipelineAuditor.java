package io.jaiclaw.pipeline;

import io.jaiclaw.audit.AuditEvent;
import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.audit.TrajectoryRecorder;
import io.jaiclaw.audit.TrajectoryStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Emits audit events and trajectory records for pipeline lifecycle events.
 * No-ops gracefully when audit or trajectory modules are absent.
 */
public class PipelineAuditor {

    private static final Logger log = LoggerFactory.getLogger(PipelineAuditor.class);
    private static final int MAX_OUTPUT_SUMMARY = 500;

    private final AuditLogger auditLogger;
    private final TrajectoryRecorder trajectoryRecorder;

    public PipelineAuditor(AuditLogger auditLogger, TrajectoryRecorder trajectoryRecorder) {
        this.auditLogger = auditLogger;
        this.trajectoryRecorder = trajectoryRecorder;
    }

    /**
     * Record that a pipeline execution has started.
     */
    public void pipelineStarted(PipelineContext ctx) {
        if (auditLogger == null) return;

        auditLogger.log(AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(ctx.tenantId())
                .actor("pipeline")
                .action("pipeline.started")
                .resource(ctx.pipelineId())
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details(Map.of(
                        "executionId", ctx.executionId(),
                        "totalStages", ctx.totalStages()
                ))
                .build());

        if (trajectoryRecorder != null) {
            trajectoryRecorder.beginSession(ctx.executionId(), ctx.tenantId());
        }
    }

    /**
     * Record that a pipeline stage has started.
     */
    public void stageStarted(PipelineContext ctx, StageDefinition stage) {
        if (auditLogger == null) return;

        auditLogger.log(AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(ctx.tenantId())
                .actor("pipeline")
                .action("pipeline.stage.started")
                .resource(ctx.pipelineId())
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details(Map.of(
                        "executionId", ctx.executionId(),
                        "stageName", stage.name(),
                        "stageType", stage.type().name(),
                        "stageIndex", ctx.stageIndex()
                ))
                .build());
    }

    /**
     * Record that a pipeline stage has completed.
     */
    public void stageCompleted(PipelineContext ctx, StageDefinition stage, Duration duration) {
        if (auditLogger == null) return;

        auditLogger.log(AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(ctx.tenantId())
                .actor("pipeline")
                .action("pipeline.stage.completed")
                .resource(ctx.pipelineId())
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details(Map.of(
                        "executionId", ctx.executionId(),
                        "stageName", stage.name(),
                        "stageType", stage.type().name(),
                        "durationMs", duration.toMillis()
                ))
                .build());

        if (trajectoryRecorder != null) {
            trajectoryRecorder.recordStep(
                    ctx.executionId(), ctx.tenantId(),
                    TrajectoryStep.StepType.SYSTEM,
                    stage.name(),
                    "Pipeline stage: " + stage.name(),
                    truncate(getLatestOutput(ctx, stage.name())),
                    0, duration,
                    Map.of("pipelineStage", stage.name())
            );
        }
    }

    /**
     * Record that a pipeline stage has failed.
     */
    public void stageFailed(PipelineContext ctx, StageDefinition stage, Throwable error) {
        if (auditLogger == null) return;

        auditLogger.log(AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(ctx.tenantId())
                .actor("pipeline")
                .action("pipeline.stage.failed")
                .resource(ctx.pipelineId())
                .outcome(AuditEvent.Outcome.FAILURE)
                .details(Map.of(
                        "executionId", ctx.executionId(),
                        "stageName", stage.name(),
                        "error", error.getMessage() != null ? error.getMessage() : error.getClass().getName()
                ))
                .build());
    }

    /**
     * Record that a pipeline execution has completed.
     */
    public void pipelineCompleted(PipelineContext ctx, Duration totalDuration) {
        if (auditLogger == null) return;

        auditLogger.log(AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(ctx.tenantId())
                .actor("pipeline")
                .action("pipeline.completed")
                .resource(ctx.pipelineId())
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details(Map.of(
                        "executionId", ctx.executionId(),
                        "totalDurationMs", totalDuration.toMillis()
                ))
                .build());

        if (trajectoryRecorder != null) {
            trajectoryRecorder.endSession(ctx.executionId());
        }
    }

    /**
     * Record that a pipeline execution has failed.
     */
    public void pipelineFailed(PipelineContext ctx, Throwable error) {
        if (auditLogger == null) return;

        auditLogger.log(AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(ctx.tenantId())
                .actor("pipeline")
                .action("pipeline.failed")
                .resource(ctx.pipelineId())
                .outcome(AuditEvent.Outcome.FAILURE)
                .details(Map.of(
                        "executionId", ctx.executionId(),
                        "error", error.getMessage() != null ? error.getMessage() : error.getClass().getName()
                ))
                .build());

        if (trajectoryRecorder != null) {
            trajectoryRecorder.endSession(ctx.executionId());
        }
    }

    private String getLatestOutput(PipelineContext ctx, String stageName) {
        PipelineContext.StageOutput output = ctx.stageOutputs().get(stageName);
        return output != null ? output.output() : "";
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_OUTPUT_SUMMARY
                ? text.substring(0, MAX_OUTPUT_SUMMARY) + "..."
                : text;
    }
}
