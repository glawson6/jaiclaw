package io.jaiclaw.voicecall.store;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.voicecall.model.CallRecord;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory call store backed by a tenant-prefixed {@link ConcurrentHashMap}.
 * Storage keys are constructed via {@link TenantGuard#resolveStorageKey(String)},
 * so two tenants' call records never collide on the {@code callId}.
 * Suitable for development and testing.
 */
public class InMemoryCallStore implements CallStore {

    private final ConcurrentHashMap<String, CallRecord> records = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;

    public InMemoryCallStore(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard != null ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
    }

    /** No-arg constructor preserved for tests and the default autoconfig path. */
    public InMemoryCallStore() {
        this(new TenantGuard(TenantProperties.DEFAULT));
    }

    private String key(String callId) {
        return tenantGuard.resolveStorageKey(callId);
    }

    @Override
    public void persist(CallRecord record) {
        records.put(key(record.getCallId()), record);
    }

    /**
     * Returns active calls. With a tenant context set, results are filtered
     * to that tenant and keys are stripped of the tenant prefix. Without a
     * context (typical for boot-time recovery in MULTI mode), every tenant's
     * active call is returned with the full {@code tenantId:callId} storage
     * key so callers can re-key into their own maps.
     */
    @Override
    public Map<String, CallRecord> loadActiveCalls() {
        if (io.jaiclaw.core.tenant.TenantContextHolder.get() == null) {
            return records.entrySet().stream()
                    .filter(e -> !e.getValue().getState().isTerminal())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        String prefix = tenantGuard.resolveStoragePrefix() + ":";
        return records.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .filter(e -> !e.getValue().getState().isTerminal())
                .collect(Collectors.toMap(
                        // strip prefix so the returned map is keyed by callId for callers
                        e -> e.getKey().substring(prefix.length()),
                        Map.Entry::getValue));
    }

    @Override
    public List<CallRecord> getHistory(int limit) {
        if (io.jaiclaw.core.tenant.TenantContextHolder.get() == null) {
            return records.values().stream()
                    .sorted(Comparator.comparing(CallRecord::getStartedAt).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        String prefix = tenantGuard.resolveStoragePrefix() + ":";
        return records.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(CallRecord::getStartedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** Total record count across all tenants. */
    public int size() {
        return records.size();
    }

    /** Wipe every tenant's records. Tests only. */
    public void clear() {
        records.clear();
    }
}
