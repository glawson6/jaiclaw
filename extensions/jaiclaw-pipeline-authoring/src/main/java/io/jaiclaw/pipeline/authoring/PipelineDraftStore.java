package io.jaiclaw.pipeline.authoring;

import java.util.List;
import java.util.Optional;

/**
 * SPI for storing {@link PipelineDraft} instances. The reference impl
 * {@link JsonFilePipelineDraftStore} lives in this module; adopters
 * can plug in Redis / JDBC / cloud-blob backends by providing their
 * own {@code @Bean PipelineDraftStore}.
 *
 * <p>Implementations must scope every operation to the current tenant
 * (via {@code TenantGuard}) when running in MULTI mode — no
 * cross-tenant read/write. In SINGLE mode this is a no-op filter.
 *
 * <p>Optimistic locking on {@link #save} is required: the incoming
 * draft's {@code revision} must equal the currently stored revision
 * for that {@code id}, or the save throws
 * {@link OptimisticLockException}. New drafts (no stored version)
 * accept any starting revision.
 */
public interface PipelineDraftStore {

    /**
     * Find a draft by id, scoped to the current tenant.
     *
     * @return the draft, or {@link Optional#empty()} if not found or
     *         owned by a different tenant.
     */
    Optional<PipelineDraft> find(String id);

    /**
     * List every draft visible to the current tenant. In SINGLE mode
     * this returns all drafts.
     */
    List<PipelineDraft> findAll();

    /**
     * Persist a draft. If a draft already exists for {@code id}, the
     * incoming {@code revision} must match the stored revision or
     * {@link OptimisticLockException} is thrown; the store then
     * writes {@code draft.withNextRevision(...)}. If no draft exists,
     * the given draft is written verbatim (its revision is preserved).
     *
     * @return the persisted draft (with the bumped revision when a
     *         previous version existed).
     */
    PipelineDraft save(PipelineDraft draft);

    /**
     * Delete a draft by id, scoped to the current tenant. No-op if
     * not found.
     */
    void delete(String id);

    /**
     * Thrown by {@link #save} when the incoming draft's revision
     * doesn't match the stored one — the caller should reload and
     * merge before retrying.
     */
    class OptimisticLockException extends RuntimeException {
        private final long expectedRevision;
        private final long actualRevision;

        public OptimisticLockException(String draftId,
                                        long expectedRevision,
                                        long actualRevision) {
            super("Draft '" + draftId + "' revision mismatch: expected "
                    + expectedRevision + ", stored " + actualRevision);
            this.expectedRevision = expectedRevision;
            this.actualRevision = actualRevision;
        }

        public long expectedRevision() { return expectedRevision; }
        public long actualRevision() { return actualRevision; }
    }
}
