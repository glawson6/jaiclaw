package io.jaiclaw.docstore.search;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.docstore.model.DocStoreEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full-text search implementation. Maintains an in-memory inverted index
 * across filenames, descriptions, tags, and extracted text.
 * In MULTI mode, search results are filtered by the current tenant.
 */
public class FullTextDocStoreSearch implements DocStoreSearchProvider {

    private final Map<String, DocStoreEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> invertedIndex = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;

    public FullTextDocStoreSearch() {
        this(null);
    }

    public FullTextDocStoreSearch(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard;
    }

    @Override
    public List<DocStoreSearchResult> search(String query, DocStoreSearchOptions options) {
        if (query == null || query.isBlank()) return List.of();

        String[] queryTerms = query.toLowerCase().split("\\s+");
        Map<String, Double> scores = new HashMap<>();

        for (String term : queryTerms) {
            invertedIndex.forEach((indexedTerm, entryIds) -> {
                if (indexedTerm.contains(term)) {
                    double termScore = indexedTerm.equals(term) ? 1.0 : 0.5;
                    for (String entryId : entryIds) {
                        scores.merge(entryId, termScore, Double::sum);
                    }
                }
            });
        }

        double maxScore = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        return scores.entrySet().stream()
                .filter(e -> entries.containsKey(e.getKey()))
                .map(e -> {
                    var entry = entries.get(e.getKey());
                    double normalized = e.getValue() / maxScore;
                    return new DocStoreSearchResult(entry, normalized, buildSnippet(entry, query));
                })
                .filter(r -> matchesOptions(r.entry(), options))
                .sorted(Comparator.comparingDouble(DocStoreSearchResult::score).reversed())
                .limit(options.maxResults())
                .toList();
    }

    @Override
    public void index(DocStoreEntry entry) {
        entries.put(entry.id(), entry);

        Set<String> terms = new HashSet<>();
        if (entry.filename() != null) addTerms(terms, entry.filename());
        if (entry.description() != null) addTerms(terms, entry.description());
        if (entry.sourceUrl() != null) addTerms(terms, entry.sourceUrl());
        if (entry.category() != null) addTerms(terms, entry.category());
        if (entry.tags() != null) entry.tags().forEach(tag -> addTerms(terms, tag));
        if (entry.analysis() != null) {
            if (entry.analysis().summary() != null) addTerms(terms, entry.analysis().summary());
            if (entry.analysis().extractedText() != null) addTerms(terms, entry.analysis().extractedText());
            entry.analysis().topics().forEach(t -> addTerms(terms, t));
            entry.analysis().entities().forEach(e -> addTerms(terms, e));
        }

        for (String term : terms) {
            invertedIndex.computeIfAbsent(term, k -> ConcurrentHashMap.newKeySet()).add(entry.id());
        }
    }

    @Override
    public void remove(String entryId) {
        entries.remove(entryId);
        invertedIndex.values().forEach(ids -> ids.remove(entryId));
    }

    private void addTerms(Set<String> terms, String text) {
        for (String word : text.toLowerCase().split("[\\s/._\\-@#]+")) {
            if (word.length() >= 2) terms.add(word);
        }
    }

    private boolean matchesOptions(DocStoreEntry entry, DocStoreSearchOptions options) {
        // Tenant filtering in MULTI mode
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (!tenantId.equals(entry.tenantId())) return false;
        }
        if (options.scopeId() != null &&
                !options.scopeId().equals(entry.userId()) &&
                !options.scopeId().equals(entry.chatId())) {
            return false;
        }
        if (options.filterTags() != null && !options.filterTags().isEmpty() &&
                (entry.tags() == null || Collections.disjoint(entry.tags(), options.filterTags()))) {
            return false;
        }
        if (options.filterMimeType() != null &&
                (entry.mimeType() == null || !entry.mimeType().startsWith(options.filterMimeType()))) {
            return false;
        }
        if (options.after() != null && entry.indexedAt().isBefore(options.after())) return false;
        if (options.before() != null && entry.indexedAt().isAfter(options.before())) return false;
        return true;
    }

    private String buildSnippet(DocStoreEntry entry, String query) {
        String text = null;
        if (entry.analysis() != null && entry.analysis().summary() != null) {
            text = entry.analysis().summary();
        } else if (entry.description() != null) {
            text = entry.description();
        } else if (entry.filename() != null) {
            text = entry.filename();
        }
        if (text == null) return "";
        return text.length() > 150 ? text.substring(0, 150) + "..." : text;
    }
}
