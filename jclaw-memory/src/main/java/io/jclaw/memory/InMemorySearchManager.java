package io.jclaw.memory;

import io.jclaw.core.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple in-memory implementation of MemorySearchManager using keyword matching.
 * Used as a fallback when no VectorStore is configured.
 * All operations are partitioned by the current tenant via {@link TenantContextHolder}.
 */
public class InMemorySearchManager implements MemorySearchManager {

    private static final Logger log = LoggerFactory.getLogger(InMemorySearchManager.class);

    private final List<MemoryEntry> entries = new CopyOnWriteArrayList<>();

    public void addEntry(String path, String content, MemorySource source) {
        String tenantId = resolveTenantId();
        entries.add(new MemoryEntry(path, content, source, tenantId));
    }

    public void addEntry(String path, String content) {
        addEntry(path, content, MemorySource.MEMORY);
    }

    /**
     * Clear entries for the current tenant only. If no tenant context is set, clears all.
     */
    public void clear() {
        String tenantId = resolveTenantId();
        if (tenantId == null) {
            entries.clear();
        } else {
            entries.removeIf(e -> tenantId.equals(e.tenantId()));
        }
    }

    /**
     * Count entries for the current tenant. If no tenant context, counts all.
     */
    public int size() {
        String tenantId = resolveTenantId();
        if (tenantId == null) {
            return entries.size();
        }
        return (int) entries.stream().filter(e -> tenantId.equals(e.tenantId())).count();
    }

    @Override
    public List<MemorySearchResult> search(String query, MemorySearchOptions options) {
        if (query == null || query.isBlank()) return List.of();

        String lowerQuery = query.toLowerCase();
        String[] queryTerms = lowerQuery.split("\\s+");
        String tenantId = resolveTenantId();

        return entries.stream()
                .filter(e -> matchesTenant(e, tenantId))
                .map(entry -> scoreEntry(entry, queryTerms))
                .filter(Objects::nonNull)
                .filter(r -> r.score() >= options.minScore())
                .sorted(Comparator.comparingDouble(MemorySearchResult::score).reversed())
                .limit(options.maxResults())
                .toList();
    }

    private boolean matchesTenant(MemoryEntry entry, String tenantId) {
        if (tenantId == null) return true;
        return tenantId.equals(entry.tenantId());
    }

    private MemorySearchResult scoreEntry(MemoryEntry entry, String[] queryTerms) {
        String lowerContent = entry.content().toLowerCase();
        int matchCount = 0;
        for (String term : queryTerms) {
            if (lowerContent.contains(term)) matchCount++;
        }

        if (matchCount == 0) return null;

        double score = (double) matchCount / queryTerms.length;
        String snippet = extractSnippet(entry.content(), queryTerms[0], 200);

        return new MemorySearchResult(
                entry.path(), 0, 0, score, snippet, entry.source());
    }

    private String extractSnippet(String content, String term, int maxLength) {
        int idx = content.toLowerCase().indexOf(term.toLowerCase());
        if (idx == -1) {
            return content.substring(0, Math.min(content.length(), maxLength));
        }
        int start = Math.max(0, idx - maxLength / 4);
        int end = Math.min(content.length(), start + maxLength);
        return (start > 0 ? "..." : "") + content.substring(start, end) + (end < content.length() ? "..." : "");
    }

    private String resolveTenantId() {
        var ctx = TenantContextHolder.get();
        return ctx != null ? ctx.getTenantId() : null;
    }

    record MemoryEntry(String path, String content, MemorySource source, String tenantId) {}
}
