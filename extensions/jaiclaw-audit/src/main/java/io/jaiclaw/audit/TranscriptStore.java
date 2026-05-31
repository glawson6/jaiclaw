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
}
