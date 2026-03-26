package io.jaiclaw.docstore.search;

import io.jaiclaw.docstore.model.DocStoreEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid search combining full-text and vector search using Reciprocal Rank Fusion (RRF).
 * Default weights: full-text 0.3, vector 0.7.
 */
public class HybridDocStoreSearch implements DocStoreSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(HybridDocStoreSearch.class);
    private static final int RRF_K = 60;

    private final FullTextDocStoreSearch fullTextSearch;
    private final VectorDocStoreSearch vectorSearch;
    private final double fullTextWeight;
    private final double vectorWeight;

    public HybridDocStoreSearch(FullTextDocStoreSearch fullTextSearch, VectorDocStoreSearch vectorSearch) {
        this(fullTextSearch, vectorSearch, 0.3, 0.7);
    }

    public HybridDocStoreSearch(FullTextDocStoreSearch fullTextSearch, VectorDocStoreSearch vectorSearch,
                                double fullTextWeight, double vectorWeight) {
        this.fullTextSearch = fullTextSearch;
        this.vectorSearch = vectorSearch;
        this.fullTextWeight = fullTextWeight;
        this.vectorWeight = vectorWeight;
    }

    @Override
    public List<DocStoreSearchResult> search(String query, DocStoreSearchOptions options) {
        if (query == null || query.isBlank()) return List.of();

        // Fetch more results from each source for better fusion
        var expandedOptions = new DocStoreSearchOptions(
                options.scopeId(),
                options.maxResults() * 3,
                options.filterTags(),
                options.filterMimeType(),
                options.after(),
                options.before()
        );

        List<DocStoreSearchResult> fullTextResults = fullTextSearch.search(query, expandedOptions);
        List<DocStoreSearchResult> vectorResults = vectorSearch.search(query, expandedOptions);

        log.debug("Hybrid search: fullText={} results, vector={} results",
                fullTextResults.size(), vectorResults.size());

        // RRF fusion
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, DocStoreSearchResult> bestResult = new LinkedHashMap<>();

        for (int i = 0; i < fullTextResults.size(); i++) {
            var r = fullTextResults.get(i);
            String id = r.entry().id();
            double rrfScore = fullTextWeight / (RRF_K + i + 1);
            rrfScores.merge(id, rrfScore, Double::sum);
            bestResult.putIfAbsent(id, r);
        }

        for (int i = 0; i < vectorResults.size(); i++) {
            var r = vectorResults.get(i);
            String id = r.entry().id();
            double rrfScore = vectorWeight / (RRF_K + i + 1);
            rrfScores.merge(id, rrfScore, Double::sum);
            bestResult.putIfAbsent(id, r);
        }

        // Sort by RRF score descending, build final results
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(options.maxResults())
                .map(e -> {
                    var original = bestResult.get(e.getKey());
                    return new DocStoreSearchResult(original.entry(), e.getValue(), original.matchSnippet());
                })
                .toList();
    }

    @Override
    public void index(DocStoreEntry entry) {
        fullTextSearch.index(entry);
        vectorSearch.index(entry);
    }

    @Override
    public void remove(String entryId) {
        fullTextSearch.remove(entryId);
        vectorSearch.remove(entryId);
    }
}
