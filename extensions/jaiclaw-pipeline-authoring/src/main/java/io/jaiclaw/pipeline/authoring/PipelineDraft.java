package io.jaiclaw.pipeline.authoring;

import io.jaiclaw.pipeline.PipelineDefinition;

import java.time.Instant;

/**
 * A draft {@link PipelineDefinition} in the Pipeline Studio's authoring
 * plane. Drafts may be invalid — the {@link PipelineDraftStore} accepts
 * them regardless so the UI can persist work-in-progress edits.
 *
 * <p>Optimistic locking uses the {@code revision} field: every save
 * bumps it by 1. A save whose incoming {@code revision} doesn't match
 * the stored revision fails with a conflict — the caller reloads and
 * merges.
 *
 * @param id             draft id, matches
 *                       {@link PipelineDefinition#id()}
 * @param revision       monotonically increasing revision number;
 *                       starts at 1 for a fresh draft
 * @param definition     the pipeline definition being edited
 * @param tenantId       tenant that owns the draft (null in SINGLE mode)
 * @param status         lifecycle status
 * @param origin         where this draft came from ({@code STUDIO},
 *                       {@code YAML_IMPORT}, {@code CODE_BEAN}), used
 *                       by the Phase-3 authorization + URI-allowlist
 *                       check
 * @param lastModifiedAt when the draft was last written
 */
public record PipelineDraft(
        String id,
        long revision,
        PipelineDefinition definition,
        String tenantId,
        Status status,
        Origin origin,
        Instant lastModifiedAt
) {
    /** Lifecycle status for a draft. */
    public enum Status {
        /** Work-in-progress; may not validate cleanly yet. */
        DRAFT,
        /** Validated successfully; ready to deploy. */
        VALIDATED,
        /** Currently deployed to the engine. */
        DEPLOYED,
        /** Retired; kept for history but not deployable. */
        DISABLED
    }

    /** Where the draft's definition originated. */
    public enum Origin {
        /** Composed in the Studio UI. */
        STUDIO,
        /** Imported from a YAML file the user uploaded. */
        YAML_IMPORT,
        /** Snapshotted from a code-defined {@code JaiClawPipeline} bean. */
        CODE_BEAN
    }

    public PipelineDraft {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Draft id must not be blank");
        }
        if (revision < 1) revision = 1;
        if (status == null) status = Status.DRAFT;
        if (origin == null) origin = Origin.STUDIO;
        if (lastModifiedAt == null) lastModifiedAt = Instant.now();
    }

    /** Return a copy with revision bumped by 1 and lastModifiedAt = now. */
    public PipelineDraft withNextRevision(PipelineDefinition nextDefinition) {
        return new PipelineDraft(id, revision + 1, nextDefinition,
                tenantId, status, origin, Instant.now());
    }

    /** Return a copy with the given status. */
    public PipelineDraft withStatus(Status newStatus) {
        return new PipelineDraft(id, revision, definition, tenantId,
                newStatus, origin, Instant.now());
    }
}
