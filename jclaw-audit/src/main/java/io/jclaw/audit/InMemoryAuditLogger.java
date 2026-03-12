package io.jclaw.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * In-memory audit logger for development and testing.
 * Stores events in a bounded deque (default 10,000 events).
 */
public class InMemoryAuditLogger implements AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAuditLogger.class);

    private final Deque<AuditEvent> events = new ConcurrentLinkedDeque<>();
    private final int maxSize;

    public InMemoryAuditLogger() {
        this(10_000);
    }

    public InMemoryAuditLogger(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public void log(AuditEvent event) {
        events.addFirst(event);
        while (events.size() > maxSize) {
            events.removeLast();
        }
        log.debug("Audit: {} {} {} -> {}", event.actor(), event.action(), event.resource(), event.outcome());
    }

    @Override
    public List<AuditEvent> query(String tenantId, int limit) {
        return events.stream()
                .filter(e -> tenantId == null || tenantId.equals(e.tenantId()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<AuditEvent> findById(String id) {
        return events.stream()
                .filter(e -> e.id().equals(id))
                .findFirst();
    }

    @Override
    public long count(String tenantId) {
        if (tenantId == null) return events.size();
        return events.stream()
                .filter(e -> tenantId.equals(e.tenantId()))
                .count();
    }

    public void clear() {
        events.clear();
    }

    public int size() {
        return events.size();
    }
}
