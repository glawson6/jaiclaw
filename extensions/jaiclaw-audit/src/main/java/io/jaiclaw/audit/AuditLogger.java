package io.jaiclaw.audit;

import java.util.List;
import java.util.Optional;

/**
 * SPI for audit logging. Implementations persist audit events to
 * a backing store (in-memory, database, external service).
 */
public interface AuditLogger {

    /**
     * Record an audit event.
     */
    void log(AuditEvent event);

    /**
     * Query audit events for a tenant, most recent first.
     *
     * @param tenantId the tenant to query (null for system-wide)
     * @param limit    maximum number of events to return
     */
    List<AuditEvent> query(String tenantId, int limit);

    /**
     * Find a specific audit event by ID.
     */
    Optional<AuditEvent> findById(String id);

    /**
     * Count audit events for a tenant.
     */
    long count(String tenantId);

    /**
     * T1-6: purge audit events for a tenant whose {@code timestamp} is
     * strictly before {@code cutoff}. Returns the number of events removed.
     * The default implementation returns 0 — impls that support real
     * deletion should override.
     *
     * <p>Callers (typically {@code RetentionEnforcementService}) invoke this
     * on their scheduled purge tick. Impls MUST scope the delete to the
     * given tenantId — never delete cross-tenant.
     *
     * @param tenantId the tenant to purge (null purges the default-tenant
     *                 partition in SINGLE mode)
     * @param cutoff   events strictly before this instant are removed
     * @return count of removed events (0 when nothing matched or impl doesn't
     *         support deletion)
     */
    default int purgeOlderThan(String tenantId, java.time.Instant cutoff) {
        return 0;
    }
}
