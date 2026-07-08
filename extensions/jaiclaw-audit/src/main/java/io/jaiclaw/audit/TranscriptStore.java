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
    /**
     * T1-6: purge transcripts for a tenant whose {@code startTime} is strictly
     * before {@code cutoff}. Returns the number of sessions removed. The
     * default implementation walks {@link #list} + {@link #load} + {@link
     * #delete} so existing stores get purge for free; impls with a real
     * index should override.
     *
     * <p>Callers (typically {@code RetentionEnforcementService}) invoke this
     * on their scheduled purge tick. Impls MUST scope the delete to the
     * given tenantId — never delete cross-tenant.
     *
     * @param tenantId the tenant to scope the purge to (null for
     *                 single-tenant / all)
     * @param cutoff   sessions strictly before this instant are removed
     * @return count of removed sessions
     */
    default int purgeOlderThan(String tenantId, java.time.Instant cutoff) {
        if (cutoff == null) return 0;
        int removed = 0;
        for (String sessionId : list(tenantId, Integer.MAX_VALUE)) {
            Optional<TranscriptSession> session = load(sessionId);
            if (session.isEmpty()) continue;
            if (session.get().startTime() != null && session.get().startTime().isBefore(cutoff)) {
                if (delete(sessionId)) removed++;
            }
        }
        return removed;
    }

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
