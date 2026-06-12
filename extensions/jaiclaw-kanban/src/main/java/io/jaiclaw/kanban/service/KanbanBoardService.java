package io.jaiclaw.kanban.service;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.persistence.BoardStore;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry of board definitions. Reads served from an in-memory cache so
 * the hot path (a transition fires a `BoardDefinition` lookup) stays free
 * of disk I/O; writes go through the optional {@link BoardStore} so they
 * survive restart (analysis §9 Q1 resolution: YAML-as-BoardStore).
 *
 * <p>Three operating modes, picked by constructor:
 * <ul>
 *   <li><b>Memory-only</b> — {@code KanbanBoardService(TenantGuard)}: no
 *       store, {@code register} writes only to the in-memory cache. Used
 *       by tests, embeddings, and the Phase 1 core wiring.</li>
 *   <li><b>Persistent, writable</b> — store + {@code writable=true}:
 *       {@code register} writes to both cache and store; {@code remove}
 *       deletes from both.</li>
 *   <li><b>Persistent, read-only</b> — store + {@code writable=false}:
 *       boot loads from the store; runtime mutations throw
 *       {@link BoardWriteException}. Useful for ops-locked deployments
 *       where boards ship via git/CI/Helm only.</li>
 * </ul>
 *
 * <p>Tenant visibility: a board with empty {@code tenantIds} is visible to
 * every tenant (the {@code PipelineDefinition} convention); otherwise only
 * to the tenants in the list. {@link #list()} filters by the current
 * {@code TenantContext} via {@link TenantGuard}.
 */
public class KanbanBoardService {

    private final ConcurrentMap<String, BoardDefinition> boards = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;
    private final BoardStore store;
    private final boolean writable;

    /** Memory-only mode. */
    public KanbanBoardService(TenantGuard tenantGuard) {
        this(tenantGuard, null, true);
    }

    public KanbanBoardService(TenantGuard tenantGuard, BoardStore store, boolean writable) {
        this.tenantGuard = tenantGuard;
        this.store = store;
        this.writable = writable;
    }

    /** Hot-cache only: register without writing to the store (used by bootstrap). */
    public void cache(BoardDefinition definition) {
        if (definition == null) return;
        boards.put(definition.id(), definition);
    }

    public void cacheAll(List<BoardDefinition> definitions) {
        if (definitions == null) return;
        for (BoardDefinition d : definitions) cache(d);
    }

    /**
     * Register a board: cache it, and if a store is configured and writes
     * are allowed, persist it. Throws {@link BoardWriteException} when a
     * store is present but {@code writable} is false.
     */
    public void register(BoardDefinition definition) {
        if (definition == null) return;
        if (store != null && !writable) {
            throw new BoardWriteException(
                    "kanban boards are read-only (jaiclaw.kanban.boards.writable=false)");
        }
        boards.put(definition.id(), definition);
        if (store != null) {
            store.save(definition);
        }
    }

    public void registerAll(List<BoardDefinition> definitions) {
        if (definitions == null) return;
        for (BoardDefinition d : definitions) register(d);
    }

    public Optional<BoardDefinition> get(String boardId) {
        BoardDefinition def = boards.get(boardId);
        if (def == null) return Optional.empty();
        if (!isVisibleToCurrentTenant(def)) return Optional.empty();
        return Optional.of(def);
    }

    public List<BoardDefinition> list() {
        return boards.values().stream()
                .filter(this::isVisibleToCurrentTenant)
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
    }

    /**
     * Remove a board: drop from cache, delete from store. Returns true when
     * something was removed. Throws {@link BoardWriteException} when a
     * store is present but {@code writable} is false.
     */
    public boolean remove(String boardId) {
        if (store != null && !writable) {
            throw new BoardWriteException(
                    "kanban boards are read-only (jaiclaw.kanban.boards.writable=false)");
        }
        boolean cacheRemoved = boards.remove(boardId) != null;
        boolean storeRemoved = store != null && store.delete(boardId);
        return cacheRemoved || storeRemoved;
    }

    /** Bypass tenant filtering — used by recovery sweeps and admin endpoints. */
    public List<BoardDefinition> listAllUnscoped() {
        return List.copyOf(boards.values());
    }

    public boolean isWritable() {
        return store == null || writable;
    }

    private boolean isVisibleToCurrentTenant(BoardDefinition def) {
        if (def.tenantIds().isEmpty()) return true;
        String current = tenantGuard.isMultiTenant()
                ? tenantGuard.requireTenantIfMulti()
                : tenantGuard.getProperties().defaultTenantId();
        return def.tenantIds().contains(current);
    }
}
