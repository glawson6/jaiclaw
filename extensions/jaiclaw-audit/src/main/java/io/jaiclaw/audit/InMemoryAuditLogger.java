package io.jaiclaw.audit;

import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * In-memory audit logger for development and testing.
 * Stores events in a bounded deque (default 10,000 events).
 * In MULTI mode, all reads are tenant-filtered via {@link TenantGuard}.
 */
public class InMemoryAuditLogger implements AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAuditLogger.class);

    private final Deque<AuditEvent> events = new ConcurrentLinkedDeque<>();
    private final int maxSize;
    private final TenantGuard tenantGuard;

    public InMemoryAuditLogger() {
        this(10_000, null);
    }

    public InMemoryAuditLogger(int maxSize) {
        this(maxSize, null);
    }

    public InMemoryAuditLogger(TenantGuard tenantGuard) {
        this(10_000, tenantGuard);
    }

    public InMemoryAuditLogger(int maxSize, TenantGuard tenantGuard) {
        this.maxSize = maxSize;
        this.tenantGuard = tenantGuard;
    }

    @Override
    public void log(AuditEvent event) {
        // Auto-stamp tenantId from TenantGuard if not set on the event
        if (event.tenantId() == null && tenantGuard != null) {
            String currentTenant = tenantGuard.requireTenantIfMulti();
            if (currentTenant != null) {
                event = new AuditEvent(event.id(), event.timestamp(), currentTenant,
                        event.actor(), event.action(), event.resource(),
                        event.outcome(), event.details());
            }
        }
        events.addFirst(event);
        while (events.size() > maxSize) {
            events.removeLast();
        }
        log.debug("Audit: {} {} {} -> {}", event.actor(), event.action(), event.resource(), event.outcome());
    }

    @Override
    public List<AuditEvent> query(String tenantId, int limit) {
        // In MULTI mode, always filter by the current tenant regardless of the tenantId parameter
        String effectiveTenant = tenantId;
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            effectiveTenant = tenantGuard.requireTenantIfMulti();
        }
        String filterTenant = effectiveTenant;
        return events.stream()
                .filter(e -> filterTenant == null || filterTenant.equals(e.tenantId()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<AuditEvent> findById(String id) {
        Optional<AuditEvent> event = events.stream()
                .filter(e -> e.id().equals(id))
                .findFirst();
        // Validate tenant in MULTI mode
        if (event.isPresent() && tenantGuard != null && tenantGuard.isMultiTenant()) {
            String currentTenant = tenantGuard.requireTenantIfMulti();
            if (!currentTenant.equals(event.get().tenantId())) {
                return Optional.empty();
            }
        }
        return event;
    }

    @Override
    public long count(String tenantId) {
        // In MULTI mode, always count for the current tenant
        String effectiveTenant = tenantId;
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            effectiveTenant = tenantGuard.requireTenantIfMulti();
        }
        if (effectiveTenant == null) return events.size();
        String filterTenant = effectiveTenant;
        return events.stream()
                .filter(e -> filterTenant.equals(e.tenantId()))
                .count();
    }

    public void clear() {
        events.clear();
    }

    public int size() {
        return events.size();
    }
}
