package io.jaiclaw.core.gdpr;

import io.jaiclaw.core.api.Stable;

import java.time.Instant;
import java.util.Map;

/**
 * T3-2 — GDPR Art. 21 objection recording. Sibling of {@link ConsentManager}
 * but semantically different: an objection means "stop processing me for this
 * purpose". Adopters use the recorded objection to short-circuit downstream
 * processing (e.g. skip profiling for a subject who objected to it).
 */
@Stable
public interface ObjectionService {

    /**
     * Record an objection from a data subject.
     *
     * @param tenantId          tenant that owns the record (never null)
     * @param dataSubjectId     subject identifier (never null)
     * @param processingPurpose e.g. {@code "profiling"}, {@code "marketing"},
     *                          {@code "automated_decision"}
     * @param proof             opaque adopter-supplied evidence (opt-out click,
     *                          email reply id, etc.). Stored verbatim.
     * @return the token adopters can reference from downstream state
     */
    ObjectionToken recordObjection(String tenantId, String dataSubjectId,
                                    String processingPurpose, String proof);

    /**
     * Rescind a previously-recorded objection. The subject is re-eligible for
     * the processing purpose. Idempotent — rescinding when none is on file
     * still emits an audit event.
     */
    void rescindObjection(String tenantId, String dataSubjectId, String processingPurpose);

    /**
     * @return true when the subject has an active objection for the given
     *         processing purpose
     */
    boolean hasObjection(String tenantId, String dataSubjectId, String processingPurpose);

    /**
     * @return map of {@code processingPurpose} → time the objection was
     *         recorded, for every active objection this subject has on file
     */
    Map<String, Instant> getObjections(String tenantId, String dataSubjectId);

    /**
     * Reference into the objection store.
     *
     * @param token             opaque token id
     * @param tenantId          tenant the objection belongs to
     * @param dataSubjectId     subject identifier
     * @param processingPurpose the purpose the subject objected to
     * @param recordedAt        when the objection was recorded
     */
    record ObjectionToken(String token, String tenantId, String dataSubjectId,
                          String processingPurpose, Instant recordedAt) {}
}
