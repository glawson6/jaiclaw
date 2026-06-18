package io.jaiclaw.pipeline.web;

import java.time.Instant;

/**
 * Consumer-safe projection of {@code PipelineExecutionSummary} returned by
 * {@code GET /api/pipelines/status/{executionId}}.
 *
 * <p>Deliberately omits {@code pipelineId}, {@code tenantId}, and the
 * internal stage-duration map so the response doesn't leak routing or
 * topology decisions back to the caller. Operators who need the full
 * record continue to have
 * {@code /actuator/pipelines/{pipelineId}/{executionId}}.
 *
 * @param id            execution UUID
 * @param status        lifecycle state: {@code RUNNING | SUCCESS | FAILED}
 * @param startedAt     when the execution began
 * @param completedAt   when it finished (nullable while {@code RUNNING})
 * @param failureReason failure message (nullable unless {@code status=FAILED})
 */
public record StatusBody(
        String id,
        String status,
        Instant startedAt,
        Instant completedAt,
        String failureReason
) {}
