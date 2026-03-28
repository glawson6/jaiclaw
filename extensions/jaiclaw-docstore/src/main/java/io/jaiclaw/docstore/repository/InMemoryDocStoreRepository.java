package io.jaiclaw.docstore.repository;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.docstore.model.DocStoreEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * In-memory DocStore repository. Suitable for testing and ephemeral use.
 * In MULTI mode, all queries are filtered by tenantId.
 */
public class InMemoryDocStoreRepository implements DocStoreRepository {

    private final Map<String, DocStoreEntry> entries = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;

    public InMemoryDocStoreRepository() {
        this(null);
    }

    public InMemoryDocStoreRepository(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard;
    }

    private Stream<DocStoreEntry> tenantFiltered() {
        Stream<DocStoreEntry> stream = entries.values().stream();
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            stream = stream.filter(e -> tenantId.equals(e.tenantId()));
        }
        return stream;
    }

    @Override
    public void save(DocStoreEntry entry) {
        entries.put(entry.id(), entry);
    }

    @Override
    public Optional<DocStoreEntry> findById(String id) {
        DocStoreEntry entry = entries.get(id);
        if (entry != null && tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (!tenantId.equals(entry.tenantId())) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(entry);
    }

    @Override
    public void deleteById(String id) {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            DocStoreEntry entry = entries.get(id);
            if (entry == null) return;
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (!tenantId.equals(entry.tenantId())) return;
        }
        entries.remove(id);
    }

    @Override
    public DocStoreEntry update(String id, UnaryOperator<DocStoreEntry> mutator) {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            return entries.computeIfPresent(id, (k, v) -> {
                if (!tenantId.equals(v.tenantId())) return v; // no-op for wrong tenant
                return mutator.apply(v);
            });
        }
        return entries.computeIfPresent(id, (k, v) -> mutator.apply(v));
    }

    @Override
    public List<DocStoreEntry> findByUserId(String userId, int limit, int offset) {
        return tenantFiltered()
                .filter(e -> userId.equals(e.userId()))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<DocStoreEntry> findByChatId(String chatId, int limit, int offset) {
        return tenantFiltered()
                .filter(e -> chatId.equals(e.chatId()))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<DocStoreEntry> findByTags(Set<String> tags, String scopeId) {
        return tenantFiltered()
                .filter(e -> matchesScope(e, scopeId))
                .filter(e -> e.tags() != null && !Collections.disjoint(e.tags(), tags))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .toList();
    }

    @Override
    public List<DocStoreEntry> findByMimeTypePrefix(String mimeTypePrefix, String scopeId) {
        return tenantFiltered()
                .filter(e -> matchesScope(e, scopeId))
                .filter(e -> e.mimeType() != null && e.mimeType().startsWith(mimeTypePrefix))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .toList();
    }

    @Override
    public List<DocStoreEntry> findRecent(String scopeId, int limit) {
        return tenantFiltered()
                .filter(e -> matchesScope(e, scopeId))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public long count(String scopeId) {
        return tenantFiltered()
                .filter(e -> matchesScope(e, scopeId))
                .count();
    }

    private boolean matchesScope(DocStoreEntry entry, String scopeId) {
        if (scopeId == null) return true;
        return scopeId.equals(entry.userId()) || scopeId.equals(entry.chatId());
    }
}
