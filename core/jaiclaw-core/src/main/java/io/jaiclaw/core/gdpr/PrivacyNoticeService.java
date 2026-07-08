package io.jaiclaw.core.gdpr;

import io.jaiclaw.core.api.Stable;

/**
 * T2-7 — SPI covering GDPR Art. 13 / 14 privacy-notice delivery.
 *
 * <p>The channel-adapter layer calls {@link #ensureNoticeDelivered} on the
 * first message from a new data subject. If a notice hasn't been shown yet,
 * the SPI:
 * <ol>
 *   <li>Returns the notice text so the caller can send it via the channel.</li>
 *   <li>Records that the subject saw the notice.</li>
 *   <li>Emits a {@code privacy.notice_displayed} audit event.</li>
 * </ol>
 *
 * <p>Returning null means the notice is already on file for this subject —
 * the caller proceeds without displaying anything.
 */
@Stable
public interface PrivacyNoticeService {

    /**
     * Ensure the privacy notice has been delivered to {@code dataSubjectId}.
     * On first call, returns the notice text; on subsequent calls, returns
     * null.
     *
     * @param tenantId      tenant that owns the notice policy
     * @param dataSubjectId subject identifier
     * @param locale        RFC 5646 tag (e.g. {@code "en-US"}, {@code "de-DE"}).
     *                      Impls MAY use it to pick a localized notice.
     * @return the notice text to display, or null if already delivered
     */
    String ensureNoticeDelivered(String tenantId, String dataSubjectId, String locale);

    /**
     * Record explicit acceptance from the subject (e.g. after the channel
     * receives the "I agree" reply). Adopters can use this to gate
     * downstream processing until acceptance is on file.
     */
    void recordAcceptance(String tenantId, String dataSubjectId);

    /**
     * @return true when the subject has previously seen the notice
     */
    boolean hasSeenNotice(String tenantId, String dataSubjectId);
}
