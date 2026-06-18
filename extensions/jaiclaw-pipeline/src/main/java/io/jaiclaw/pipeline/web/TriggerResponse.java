package io.jaiclaw.pipeline.web;

import java.time.Instant;

/**
 * Opaque response body returned by {@code POST /api/pipelines/trigger}.
 *
 * <p>Deliberately omits the internal pipeline id (which
 * {@code PipelineExecutionHandle} carries) so the API consumer never learns
 * which pipeline ran. The {@link #id} is the same UUID assigned to the
 * underlying execution; pass it to {@code GET /api/pipelines/status/{id}}
 * to check progress.
 *
 * @param id          execution UUID
 * @param submittedAt server-clock instant at which the gateway accepted the
 *                    request
 */
public record TriggerResponse(String id, Instant submittedAt) {}
