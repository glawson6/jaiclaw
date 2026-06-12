package io.jaiclaw.tasks.persistence;

import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskStatus;
import io.jaiclaw.tasks.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link TaskStore} that routes each call to a per-tenant backing store,
 * resolved through {@link TenantGuard} at call time. Analysis §6.7 and
 * plan §9 group 4.
 *
 * <p>This is a <b>routing</b> layer, not an isolation layer — each
 * backing store owns its own isolation mechanics (key prefixing, tenant
 * subdirectories, {@code WHERE tenant_id = ?}). The router guarantees
 * only that the right backend is selected for the current tenant.
 *
 * <p>Backends are registered explicitly (typically by autoconfig
 * reading {@code jaiclaw.tasks.storage.tenants[*]}) — the router does
 * not create them. In {@code SINGLE} mode (no tenant context, or the
 * tenant guard reports SINGLE), every call hits the default store. In
 * {@code MULTI} mode, the current tenant id is the routing key; a
 * tenant with no explicit backend falls back to the default store.
 *
 * <p>Phase 1's {@code KanbanRecoveryManager} previously called
 * {@code taskStore.findAll()} once; in routed mode it needs to iterate
 * each tenant's store. {@link #tenantStores()} exposes the
 * {@code (tenantId, store)} pairs so the recovery sweep can set the
 * tenant context per pass — see
 * {@link io.jaiclaw.tasks.persistence.TenantRoutingTaskStore#withTenant}
 * for the convenience helper.
 */
public class TenantRoutingTaskStore implements TaskStore {

    private static final Logger log = LoggerFactory.getLogger(TenantRoutingTaskStore.class);

    private final TaskStore defaultStore;
    private final ConcurrentMap<String, TaskStore> byTenant = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;

    public TenantRoutingTaskStore(TaskStore defaultStore, TenantGuard tenantGuard) {
        this.defaultStore = defaultStore;
        this.tenantGuard = tenantGuard;
    }

    /** Register a per-tenant backend. Overwrites any prior registration. */
    public void register(String tenantId, TaskStore store) {
        if (tenantId == null || store == null) return;
        byTenant.put(tenantId, store);
    }

    /** Remove a tenant's backend; subsequent calls under that tenant fall back to default. */
    public TaskStore deregister(String tenantId) {
        return byTenant.remove(tenantId);
    }

    /**
     * Snapshot of {@code (tenantId, store)} pairs explicitly registered.
     * The default store is <b>not</b> included — callers that need
     * "every backend" should consult {@link #defaultStore()} alongside.
     */
    public Map<String, TaskStore> tenantStores() {
        return new HashMap<>(byTenant);
    }

    public TaskStore defaultStore() {
        return defaultStore;
    }

    /**
     * Run {@code action} with the {@link TenantContextHolder} set to
     * {@code tenantId}. Restores the prior context on exit. Lets the
     * recovery sweep do {@code withTenant(t, () -> store.findAll())}
     * without thread-local leakage.
     */
    public static void withTenant(String tenantId, Runnable action) {
        var prior = TenantContextHolder.get();
        try {
            TenantContextHolder.set(new io.jaiclaw.core.tenant.DefaultTenantContext(tenantId, tenantId));
            action.run();
        } finally {
            if (prior != null) {
                TenantContextHolder.set(prior);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    private TaskStore route() {
        if (tenantGuard == null || !tenantGuard.isMultiTenant()) {
            return defaultStore;
        }
        String tenantId = tenantGuard.requireTenantIfMulti();
        TaskStore store = byTenant.get(tenantId);
        if (store != null) return store;
        log.debug("No tenant-specific TaskStore registered for tenant {}; using default", tenantId);
        return defaultStore;
    }

    // ── TaskStore delegation ────────────────────────────────────────

    @Override public void save(TaskRecord task) { route().save(task); }

    @Override public Optional<TaskRecord> compareAndSave(TaskRecord task) { return route().compareAndSave(task); }

    @Override public Optional<TaskRecord> findById(String id) { return route().findById(id); }

    @Override public List<TaskRecord> findByStatus(TaskStatus status) { return route().findByStatus(status); }

    @Override public List<TaskRecord> findByBoardAndState(String boardId, String state) {
        return route().findByBoardAndState(boardId, state);
    }

    @Override public List<TaskRecord> findAll() { return route().findAll(); }

    @Override public void deleteById(String id) { route().deleteById(id); }

    @Override public long count() { return route().count(); }
}
