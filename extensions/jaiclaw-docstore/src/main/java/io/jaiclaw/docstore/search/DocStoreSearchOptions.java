package io.jaiclaw.docstore.search;

import java.time.Instant;
import java.util.Set;

/**
 * Options for filtering and limiting DocStore search results.
 *
 * @param scopeId        userId or chatId to scope the search
 * @param maxResults     maximum results to return (default 10)
 * @param filterTags     optional tag filter (entries must match at least one)
 * @param filterMimeType optional MIME type prefix filter (e.g. "application/pdf")
 * @param after          only entries indexed after this time
 * @param before         only entries indexed before this time
 */
public record DocStoreSearchOptions(
        String scopeId,
        int maxResults,
        Set<String> filterTags,
        String filterMimeType,
        Instant after,
        Instant before
) {
    public static final DocStoreSearchOptions DEFAULT = new DocStoreSearchOptions(null, 10, null, null, null, null);

    public DocStoreSearchOptions {
        if (maxResults <= 0) maxResults = 10;
    }
}
