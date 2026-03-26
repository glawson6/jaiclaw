package io.jaiclaw.docstore.search;

import io.jaiclaw.docstore.model.DocStoreEntry;

/**
 * A search result with the matching entry, relevance score, and a text snippet.
 *
 * @param entry        the matching DocStore entry
 * @param score        relevance score (0.0–1.0)
 * @param matchSnippet text snippet showing the match context
 */
public record DocStoreSearchResult(
        DocStoreEntry entry,
        double score,
        String matchSnippet
) {}
