package io.jaiclaw.pipeline.web;

import java.util.Map;

/**
 * Trigger resource accepted by {@code POST /api/pipelines/trigger}.
 *
 * <p>The {@link #pipeline} field carries a <b>logical alias</b>, not an
 * internal pipeline id. The framework resolves the alias against
 * {@code jaiclaw.pipeline.http-trigger.allowed} (alias → pipelineId map)
 * configured by the operator. Aliases not in the map produce 404. Pipeline
 * ids never appear in the API surface, so URL/parameter tampering can't
 * reach a non-public pipeline.
 *
 * @param pipeline      logical alias (required, non-blank); resolved server-side
 * @param payload       string body handed to the first stage of the matched
 *                      pipeline (nullable → empty string). Callers wanting
 *                      structured input should JSON-encode their own object
 *                      into this string.
 * @param correlationId optional caller-supplied tracing id. If present,
 *                      threaded into {@code PipelineContext.correlationId()}
 *                      so logs / traces / audit entries can stitch together.
 *                      Header {@code X-Correlation-Id} takes precedence when
 *                      both are supplied.
 * @param tenantId      optional tenant id for multi-tenant deployments. Header
 *                      {@code X-Tenant-Id} takes precedence when both are
 *                      supplied.
 * @param metadata      optional free-form caller-supplied tags (e.g.
 *                      {@code {"source":"telegram-bot","user":"alice"}}).
 *                      Accepted by the API for forward compatibility — the
 *                      gateway signature doesn't carry arbitrary metadata
 *                      today, so v1 just logs it at INFO and drops. Wiring
 *                      it into {@code PipelineContext.metadata()} is a
 *                      follow-up.
 */
public record PipelineTriggerRequest(
        String pipeline,
        String payload,
        String correlationId,
        String tenantId,
        Map<String, String> metadata
) {
    public PipelineTriggerRequest {
        if (payload == null) payload = "";
        if (metadata == null) metadata = Map.of();
    }
}
