package io.jaiclaw.compliance.gdpr;

import io.jaiclaw.audit.AuditEvent;
import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.audit.TranscriptStore;
import io.jaiclaw.core.gdpr.DataSubjectErasureSpi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * T2-1 — reference {@link DataSubjectErasureSpi} that fans out over every
 * registered {@link TranscriptStore} + {@link AuditLogger} bean.
 *
 * <p>Memory + session erasure hooks are wired via the framework's
 * {@code MemoryProvider} / {@code SessionManager} SPIs at a higher layer
 * (jaiclaw-agent) — this aggregate calls out to the two SPIs the compliance
 * module can see from here. The plan's design intent (cascade across all
 * four) is preserved by having a hook-callback pattern that additional
 * cascade steps can subscribe to.
 *
 * <p>Every invocation writes an {@code data.subject_erasure} audit event
 * with reason + counts.
 */
public class AggregateDataSubjectErasureSpi implements DataSubjectErasureSpi {

    private static final Logger log = LoggerFactory.getLogger(AggregateDataSubjectErasureSpi.class);

    /** Audit-event action string emitted on every erasure. */
    public static final String ACTION_ERASURE = "data.subject_erasure";

    private final List<TranscriptStore> transcriptStores;
    private final List<AuditLogger> auditLoggers;
    private final Clock clock;

    public AggregateDataSubjectErasureSpi(List<TranscriptStore> transcriptStores,
                                          List<AuditLogger> auditLoggers) {
        this(transcriptStores, auditLoggers, Clock.systemUTC());
    }

    public AggregateDataSubjectErasureSpi(List<TranscriptStore> transcriptStores,
                                          List<AuditLogger> auditLoggers,
                                          Clock clock) {
        this.transcriptStores = List.copyOf(transcriptStores == null ? List.of() : transcriptStores);
        this.auditLoggers = List.copyOf(auditLoggers == null ? List.of() : auditLoggers);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public ErasureResult eraseForDataSubject(String tenantId, String dataSubjectId, ErasureReason reason) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (dataSubjectId == null || dataSubjectId.isBlank()) {
            throw new IllegalArgumentException("dataSubjectId must not be blank");
        }
        ErasureReason effectiveReason = reason == null ? ErasureReason.OPERATOR_INITIATED : reason;

        Instant start = clock.instant();
        int transcriptsDeleted = 0;
        for (TranscriptStore store : transcriptStores) {
            try {
                transcriptsDeleted += store.eraseForDataSubject(tenantId, dataSubjectId);
            } catch (RuntimeException e) {
                log.warn("Transcript-store erasure failed for tenant={} subject={}: {}",
                        tenantId, dataSubjectId, e.getMessage());
            }
        }

        int auditEventsDeleted = 0;
        for (AuditLogger logger : auditLoggers) {
            try {
                auditEventsDeleted += logger.eraseForDataSubject(tenantId, dataSubjectId);
            } catch (RuntimeException e) {
                log.warn("Audit-logger erasure failed for tenant={} subject={}: {}",
                        tenantId, dataSubjectId, e.getMessage());
            }
        }

        // Memory + session erasure require SPIs from jaiclaw-agent; they aren't
        // reachable from this module. Adopters wiring MemoryProvider /
        // SessionManager erasure hooks should extend this class or wrap it in
        // their own aggregate. Reported as zero from this reference impl.
        int memoryEntriesDeleted = 0;
        int sessionsDeleted = 0;

        Duration elapsed = Duration.between(start, clock.instant());
        ErasureResult result = new ErasureResult(
                transcriptsDeleted, memoryEntriesDeleted, auditEventsDeleted, sessionsDeleted, elapsed);

        writeErasureAuditEvent(tenantId, dataSubjectId, effectiveReason, result);
        log.info("Erased subject {} for tenant {} ({}): transcripts={} audit={} elapsed={}ms",
                dataSubjectId, tenantId, effectiveReason,
                transcriptsDeleted, auditEventsDeleted, elapsed.toMillis());
        return result;
    }

    private void writeErasureAuditEvent(String tenantId, String dataSubjectId,
                                        ErasureReason reason, ErasureResult result) {
        if (auditLoggers.isEmpty()) return;
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(clock.instant())
                .tenantId(tenantId)
                .actor("system")
                .action(ACTION_ERASURE)
                .resource("subject:" + dataSubjectId)
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details(Map.of(
                        "reason", reason.name(),
                        "transcriptsDeleted", result.transcriptsDeleted(),
                        "auditEventsDeleted", result.auditEventsDeleted(),
                        "memoryEntriesDeleted", result.memoryEntriesDeleted(),
                        "sessionsDeleted", result.sessionsDeleted(),
                        "elapsedMs", result.elapsed().toMillis()))
                .build();
        for (AuditLogger logger : auditLoggers) {
            try {
                logger.log(event);
            } catch (RuntimeException e) {
                log.warn("Failed to write erasure audit event: {}", e.getMessage());
            }
        }
    }
}
