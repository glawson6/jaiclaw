package io.jaiclaw.docstore.repository;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.docstore.model.DocStoreEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * In-memory DocStore repository. Suitable for testing and ephemeral use.
 *
 * <p>Keys are {@code "{tenantId}:{entryId}"} so two tenants using the same
 * business-domain entry id do not collide on save. Reads always filter by
 * the current tenant prefix, then check the value's {@code tenantId}
 * field as a secondary safety net.
 */
public class InMemoryDocStoreRepository implements DocStoreRepository {

    private final Map<String, DocStoreEntry> entries = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;

    public InMemoryDocStoreRepository() {
        this(new TenantGuard(TenantProperties.DEFAULT));
    }

    public InMemoryDocStoreRepository(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard != null ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
    }

    /** Current tenant id (default-tenant-id in SINGLE, throws in MULTI with no context). */
    private String currentTenantId() {
        if (tenantGuard.isMultiTenant()) {
            return tenantGuard.requireTenantIfMulti();
        }
        return tenantGuard.getProperties().defaultTenantId();
    }

    /** Storage key for the given entry. Uses the entry's own tenantId when present. */
    private String storageKey(DocStoreEntry entry) {
        String tenantId = entry.tenantId() != null ? entry.tenantId() : currentTenantId();
        return tenantId + ":" + entry.id();
    }

    /** Storage key for the given entry id, scoped to the current tenant. */
    private String storageKey(String entryId) {
        return currentTenantId() + ":" + entryId;
    }

    /** Stream of entries visible to the current tenant. */
    private Stream<DocStoreEntry> tenantFiltered() {
        String prefix = currentTenantId() + ":";
        return entries.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue);
    }

    @Override
    public void save(DocStoreEntry entry) {
        entries.put(storageKey(entry), entry);
    }

    @Override
    public Optional<DocStoreEntry> findById(String id) {
        return Optional.ofNullable(entries.get(storageKey(id)));
    }

    @Override
    public void deleteById(String id) {
        entries.remove(storageKey(id));
    }

    @Override
    public DocStoreEntry update(String id, UnaryOperator<DocStoreEntry> mutator) {
        return entries.computeIfPresent(storageKey(id), (k, v) -> mutator.apply(v));
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
