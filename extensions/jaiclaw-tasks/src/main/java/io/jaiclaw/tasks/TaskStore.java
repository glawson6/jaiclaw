package io.jaiclaw.tasks;

import java.util.List;
import java.util.Optional;

/**
 * SPI for task persistence.
 *
 * <p>Implementations are expected to be tenant-aware — reads are scoped to the
 * current {@code TenantContext} and writes carry the task's {@code tenantId}.
 * Per-tenant isolation mechanics (key prefixing, separate directories,
 * {@code WHERE tenant_id = ?}) live inside each implementation.
 */
public interface TaskStore {

    /** Persist a task, overwriting any prior version with the same id under the current tenant. */
    void save(TaskRecord task);

    /**
     * Optimistic compare-and-save. Succeeds and returns the persisted record
     * (with {@code version} incremented by one) only when the on-disk version
     * matches {@code task.version()}. Returns {@link Optional#empty()} when a
     * concurrent write has advanced the stored version — the caller must
     * re-read and re-validate the operation it intended.
     *
     * <p>Implementations must also write the updated version atomically
     * with the conflict check so two callers cannot both succeed against the
     * same source version.
     */
    Optional<TaskRecord> compareAndSave(TaskRecord task);

    Optional<TaskRecord> findById(String id);

    List<TaskRecord> findByStatus(TaskStatus status);

    /**
     * Records on the given kanban board currently sitting in the given state.
     * Used for WIP-limit checks and column-ordered snapshots.
     */
    List<TaskRecord> findByBoardAndState(String boardId, String state);

    List<TaskRecord> findAll();

    void deleteById(String id);

    long count();
}
