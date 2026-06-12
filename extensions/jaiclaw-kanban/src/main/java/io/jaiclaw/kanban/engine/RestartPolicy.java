package io.jaiclaw.kanban.engine;

/**
 * What the recovery manager does with a RUNNING (or QUEUED) card found on
 * a processor column at startup (or after a stale-running timeout).
 * Per-column override via {@code ColumnDefinition.processor.restartPolicy}.
 *
 * <p>Plan §6.2 + analysis §6.2.
 */
public enum RestartPolicy {
    /**
     * Fire the column's {@code onFailure} event with reason
     * {@code "interrupted by restart"} — card lands in the
     * configured failure column for human/agent triage. Default for
     * side-effectful work.
     */
    FAIL,

    /**
     * Re-submit the processor with the same {@link io.jaiclaw.kanban.idempotency.IdempotencyKeyBuilder}
     * key. Capped by {@code maxAttempts}; on exhaustion falls back to
     * {@link #FAIL}. Only legal on {@code idempotent: true} columns
     * (enforced by {@code BoardValidator}).
     */
    REQUEUE,

    /**
     * Leave the card in place but mark
     * {@code metadata["kanban.interrupted"]=true} for operator-driven
     * environments. No automatic re-execution.
     */
    MANUAL;

    /** Parse from {@code ColumnDefinition.processor.restartPolicy()}; null → {@link #FAIL}. */
    public static RestartPolicy parse(String raw) {
        if (raw == null || raw.isBlank()) return FAIL;
        try { return RestartPolicy.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return FAIL; }
    }
}
