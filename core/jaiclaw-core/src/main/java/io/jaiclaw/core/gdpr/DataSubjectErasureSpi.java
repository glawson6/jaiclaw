package io.jaiclaw.core.gdpr;

import io.jaiclaw.core.api.Stable;

import java.time.Duration;

/**
 * T2-1 — GDPR Art. 17 "right to erasure" SPI.
 *
 * <p>Implementations cascade the delete of every stored artifact for a data
 * subject across the framework's persistence layers (transcripts, memory,
 * audit, sessions). The reference {@code AggregateDataSubjectErasureSpi}
 * (in {@code jaiclaw-compliance}) composes over registered
 * {@code TranscriptStore}, {@code MemoryProvider}, {@code AuditLogger}, and
 * {@code SessionManager} beans.
 *
 * <p>Contract:
 * <ul>
 *   <li>Erasure MUST be tenant-scoped — tenant A cannot erase tenant B's data
 *       even given the same {@code dataSubjectId}.</li>
 *   <li>An erasure audit event MUST be emitted with the reason and counts.
 *       The audit deletion for the subject is a soft-delete + tombstone
 *       (content erased, event shell preserved) so the audit-of-erasure
 *       itself survives — required by GDPR Art. 30 + HIPAA §164.312(b).</li>
 *   <li>Erasure operates on the primary. Backup + replica erasure is the
 *       deployer's responsibility (per the plan's risk callout #4).</li>
 * </ul>
 */
@Stable
public interface DataSubjectErasureSpi {

    /**
     * Delete every stored artifact for a data subject within a tenant.
     *
     * @param tenantId      the tenant that owns the subject's data (never null)
     * @param dataSubjectId the subject identifier (typically the peerId
     *                      component of the JaiClaw session key convention
     *                      {@code {agentId}:{channel}:{accountId}:{peerId}})
     * @param reason        the erasure reason (drives the audit event)
     * @return summary of what was erased
     */
    ErasureResult eraseForDataSubject(String tenantId, String dataSubjectId, ErasureReason reason);

    /**
     * Structured result of an erasure operation.
     *
     * @param transcriptsDeleted  count of transcript sessions removed
     * @param memoryEntriesDeleted count of memory rows removed
     * @param auditEventsDeleted  count of audit events soft-deleted (content
     *                            erased, shell preserved)
     * @param sessionsDeleted     count of active sessions closed
     * @param elapsed             wall-clock time for the operation
     */
    record ErasureResult(
            int transcriptsDeleted,
            int memoryEntriesDeleted,
            int auditEventsDeleted,
            int sessionsDeleted,
            Duration elapsed
    ) {
        public static ErasureResult empty(Duration elapsed) {
            return new ErasureResult(0, 0, 0, 0, elapsed);
        }

        public int totalDeleted() {
            return transcriptsDeleted + memoryEntriesDeleted + auditEventsDeleted + sessionsDeleted;
        }
    }

    /**
     * Why erasure was triggered. Recorded on the audit event so a regulator
     * can distinguish a data-subject request from a scheduled retention purge.
     */
    enum ErasureReason {
        /** GDPR Art. 17 request from the data subject. */
        ART_17_REQUEST,
        /** {@code RetentionEnforcementService} tick found data past its TTL. */
        RETENTION_EXPIRY,
        /** {@code ConsentManager} recorded a withdrawal (T2-5). */
        CONSENT_WITHDRAWAL,
        /** Operator-initiated erasure via CLI or admin API. */
        OPERATOR_INITIATED
    }
}
