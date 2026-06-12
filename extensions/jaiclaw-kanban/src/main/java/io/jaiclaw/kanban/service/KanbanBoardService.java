package io.jaiclaw.kanban.service;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.kanban.model.BoardDefinition;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory registry of board definitions. Phase 2 may add a writable
 * {@code BoardStore} behind the same surface — that decision is analysis
 * §9 Q1, tracked as a blocker in the implementation plan before the REST
 * controller PR merges.
 *
 * <p>Tenant visibility: a board with empty {@code tenantIds} is visible to
 * every tenant (the {@code PipelineDefinition} convention); otherwise only
 * to the tenants in the list. {@link #list()} filters by the current
 * {@code TenantContext} via {@link TenantGuard}.
 */
public class KanbanBoardService {

    private final ConcurrentMap<String, BoardDefinition> boards = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;

    public KanbanBoardService(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard;
    }

    /** Register or replace a board definition. */
    public void register(BoardDefinition definition) {
        if (definition == null) return;
        boards.put(definition.id(), definition);
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

    public boolean remove(String boardId) {
        return boards.remove(boardId) != null;
    }

    /** Bypass tenant filtering — used by recovery sweeps and Phase 2 admin endpoints. */
    public List<BoardDefinition> listAllUnscoped() {
        return List.copyOf(boards.values());
    }

    private boolean isVisibleToCurrentTenant(BoardDefinition def) {
        if (def.tenantIds().isEmpty()) return true;
        String current = tenantGuard.isMultiTenant()
                ? tenantGuard.requireTenantIfMulti()
                : tenantGuard.getProperties().defaultTenantId();
        return def.tenantIds().contains(current);
    }
}
