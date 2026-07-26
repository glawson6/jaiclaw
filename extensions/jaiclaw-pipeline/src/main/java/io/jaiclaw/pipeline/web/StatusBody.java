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
 * @param result        caller-visible result string on {@code SUCCESS}
 *                      (nullable while running or on failure). Runtime
 *                      populates from a stage's {@code metadata.__result__},
 *                      the definition's {@code resultTemplate}, or the
 *                      fallback {@code "SUCCESS"}. Truncated at 4 KB.
 */
public record StatusBody(
        String id,
        String status,
        Instant startedAt,
        Instant completedAt,
        String failureReason,
        String result
) {}
