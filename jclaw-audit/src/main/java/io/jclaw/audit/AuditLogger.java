package io.jclaw.audit;

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
}
