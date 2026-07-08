package io.jaiclaw.core.gdpr;

import io.jaiclaw.core.api.Stable;

import java.time.Instant;
import java.util.Map;

/**
 * T2-5 — SPI for recording GDPR Art. 6 / Art. 7 consent and Art. 21 withdrawals.
 *
 * <p>Every recorded consent + withdrawal is expected to emit an audit event —
 * the SPI itself doesn't mandate the mechanism, but reference impls in
 * {@code jaiclaw-compliance} route through the registered
 * {@code AuditLogger} beans. {@code TenantContext.getConsentToken()}
 * (T1-1 metadata) becomes the reference from a session back into a
 * {@link ConsentToken}.
 */
@Stable
public interface ConsentManager {

    /**
     * Record explicit consent for a data subject.
     *
     * @param tenantId      tenant that owns the record (never null)
     * @param dataSubjectId subject identifier (never null)
     * @param consentType   consent kind (e.g. {@code "processing"},
     *                      {@code "marketing"}, {@code "profiling"})
     * @param proof         opaque evidence blob — signed cookie, click-log
     *                      row id, UI screenshot hash. Stored verbatim.
     * @return the token adopters can reference from downstream state
     */
    ConsentToken recordConsent(String tenantId, String dataSubjectId, String consentType, String proof);

    /**
     * Withdraw a previously-recorded consent. Idempotent — withdrawing a
     * consent that was never recorded is a no-op that still emits an audit
     * event so operators can distinguish "no consent" from "explicit refusal".
     *
     * @param tenantId      tenant that owns the record
     * @param dataSubjectId subject identifier
     * @param consentType   the consent kind being withdrawn
     */
    void withdrawConsent(String tenantId, String dataSubjectId, String consentType);

    /**
     * @return map of {@code consentType} → time last confirmed. An empty map
     *         means no consent has ever been recorded (or all were withdrawn).
     */
    Map<String, Instant> getConsentStatus(String tenantId, String dataSubjectId);

    /**
     * Reference into the consent store carried on session state via
     * {@code TenantContext.getConsentToken()}.
     *
     * @param token         opaque token id (typically a UUID)
     * @param tenantId      tenant the consent belongs to
     * @param dataSubjectId subject identifier
     * @param consentType   the kind of consent recorded
     * @param recordedAt    when the consent was recorded
     */
    record ConsentToken(String token, String tenantId, String dataSubjectId, String consentType, Instant recordedAt) {}
}
