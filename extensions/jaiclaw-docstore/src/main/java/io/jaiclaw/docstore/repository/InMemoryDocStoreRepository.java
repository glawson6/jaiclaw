package io.jaiclaw.docstore.repository;

import io.jaiclaw.docstore.model.DocStoreEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * In-memory DocStore repository. Suitable for testing and ephemeral use.
 */
public class InMemoryDocStoreRepository implements DocStoreRepository {

    private final Map<String, DocStoreEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void save(DocStoreEntry entry) {
        entries.put(entry.id(), entry);
    }

    @Override
    public Optional<DocStoreEntry> findById(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public void deleteById(String id) {
        entries.remove(id);
    }

    @Override
    public DocStoreEntry update(String id, UnaryOperator<DocStoreEntry> mutator) {
        return entries.computeIfPresent(id, (k, v) -> mutator.apply(v));
    }

    @Override
    public List<DocStoreEntry> findByUserId(String userId, int limit, int offset) {
        return entries.values().stream()
                .filter(e -> userId.equals(e.userId()))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<DocStoreEntry> findByChatId(String chatId, int limit, int offset) {
        return entries.values().stream()
                .filter(e -> chatId.equals(e.chatId()))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<DocStoreEntry> findByTags(Set<String> tags, String scopeId) {
        return entries.values().stream()
                .filter(e -> matchesScope(e, scopeId))
                .filter(e -> e.tags() != null && !Collections.disjoint(e.tags(), tags))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .toList();
    }

    @Override
    public List<DocStoreEntry> findByMimeTypePrefix(String mimeTypePrefix, String scopeId) {
        return entries.values().stream()
                .filter(e -> matchesScope(e, scopeId))
                .filter(e -> e.mimeType() != null && e.mimeType().startsWith(mimeTypePrefix))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .toList();
    }

    @Override
    public List<DocStoreEntry> findRecent(String scopeId, int limit) {
        return entries.values().stream()
                .filter(e -> matchesScope(e, scopeId))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public long count(String scopeId) {
        return entries.values().stream()
                .filter(e -> matchesScope(e, scopeId))
                .count();
    }

    private boolean matchesScope(DocStoreEntry entry, String scopeId) {
        if (scopeId == null) return true;
        return scopeId.equals(entry.userId()) || scopeId.equals(entry.chatId());
    }
}
