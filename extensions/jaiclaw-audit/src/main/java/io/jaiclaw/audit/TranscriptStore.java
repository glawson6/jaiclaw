package io.jaiclaw.audit;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting conversation transcripts.
 */
public interface TranscriptStore {

    /**
     * Save or update a transcript session.
     */
    void save(TranscriptSession session);

    /**
     * Load a transcript by session ID.
     */
    Optional<TranscriptSession> load(String sessionId);

    /**
     * List session IDs for a tenant, most recent first.
     *
     * @param tenantId the tenant to query (null for single-tenant / all)
     * @param limit    maximum number of session IDs to return
     */
    List<String> list(String tenantId, int limit);

    /**
     * Delete a transcript by session ID.
     *
     * @return true if a transcript was deleted, false if not found
     */
    boolean delete(String sessionId);

    /**
     * Search stored transcripts for utterances whose content contains
     * {@code query} (case-insensitive substring match). Returns most-recent
     * matches first. Implementations MAY scan all sessions or use an index;
     * the SPI does not mandate either.
     *
     * <p>Default implementation is a naive linear scan built on {@link #list}
     * and {@link #load} so that existing stores get {@code search} for free.
     * Store implementations with a real index (Lucene, SQLite FTS5, etc.)
     * should override this method.
     *
     * @param query    the substring to search for; blank returns empty list
     * @param tenantId the tenant to scope the search to (null for
     *                 single-tenant / all)
     * @param limit    maximum number of matches to return
     * @return matches, most-recent-session first, at most {@code limit} items
     */
    default List<TranscriptSearchResult> search(String query, String tenantId, int limit) {
        if (query == null || query.isBlank() || limit <= 0) return List.of();
        String needle = query.toLowerCase();
        List<TranscriptSearchResult> results = new java.util.ArrayList<>();
        for (String sessionId : list(tenantId, Integer.MAX_VALUE)) {
            if (results.size() >= limit) break;
            Optional<TranscriptSession> session = load(sessionId);
            if (session.isEmpty()) continue;
            TranscriptSession s = session.get();
            for (TranscriptUtterance u : s.utterances()) {
                if (u.content() != null && u.content().toLowerCase().contains(needle)) {
                    results.add(new TranscriptSearchResult(
                            s.sessionId(), s.startTime(), s.channel(), s.agentId(), u));
                    break; // one match per session is enough for the summary
                }
            }
        }
        return results;
    }
}
