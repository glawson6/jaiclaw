package io.jaiclaw.compliance.gdpr;

import io.jaiclaw.audit.AuditEvent;
import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.audit.TranscriptSession;
import io.jaiclaw.audit.TranscriptStore;
import io.jaiclaw.core.gdpr.DataSubjectExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * T2-2 reference impl: collects a data subject's records from all registered
 * {@link TranscriptStore} + {@link AuditLogger} beans and returns them in the
 * requested {@link ExportFormat}.
 *
 * <p>Transcripts are matched by the session-key convention
 * {@code {agentId}:{channel}:{accountId}:{peerId}} — an entry belongs to the
 * subject when its {@code sessionId} equals or ends with
 * {@code ":<dataSubjectId>"}. Audit events are matched by their
 * {@code resource} field ending with {@code ":<dataSubjectId>"} — impls
 * needing a stricter match should extend this class.
 */
public class AggregateDataSubjectExportService implements DataSubjectExportService {

    private static final Logger log = LoggerFactory.getLogger(AggregateDataSubjectExportService.class);

    private final List<TranscriptStore> transcriptStores;
    private final List<AuditLogger> auditLoggers;
    private final Clock clock;

    public AggregateDataSubjectExportService(List<TranscriptStore> transcriptStores,
                                             List<AuditLogger> auditLoggers) {
        this(transcriptStores, auditLoggers, Clock.systemUTC());
    }

    public AggregateDataSubjectExportService(List<TranscriptStore> transcriptStores,
                                             List<AuditLogger> auditLoggers, Clock clock) {
        this.transcriptStores = List.copyOf(transcriptStores == null ? List.of() : transcriptStores);
        this.auditLoggers = List.copyOf(auditLoggers == null ? List.of() : auditLoggers);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public DataSubjectExport exportForDataSubject(String tenantId, String dataSubjectId, ExportFormat format) {
        if (tenantId == null || tenantId.isBlank())
            throw new IllegalArgumentException("tenantId must not be blank");
        if (dataSubjectId == null || dataSubjectId.isBlank())
            throw new IllegalArgumentException("dataSubjectId must not be blank");
        ExportFormat fmt = format == null ? ExportFormat.JSON : format;
        String suffix = ":" + dataSubjectId;

        List<Map<String, Object>> transcripts = new ArrayList<>();
        for (TranscriptStore store : transcriptStores) {
            try {
                for (String id : store.list(tenantId, Integer.MAX_VALUE)) {
                    if (id == null) continue;
                    if (!(id.equals(dataSubjectId) || id.endsWith(suffix))) continue;
                    store.load(id).ifPresent(s -> transcripts.add(toTranscriptMap(s, fmt)));
                }
            } catch (RuntimeException e) {
                log.warn("Transcript-store export failed for tenant={} subject={}: {}",
                        tenantId, dataSubjectId, e.getMessage());
            }
        }

        List<Map<String, Object>> auditEvents = new ArrayList<>();
        for (AuditLogger logger : auditLoggers) {
            try {
                for (AuditEvent evt : logger.query(tenantId, Integer.MAX_VALUE)) {
                    if (evt.resource() != null &&
                            (evt.resource().equals(dataSubjectId) || evt.resource().endsWith(suffix))) {
                        auditEvents.add(toAuditMap(evt, fmt));
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Audit-logger export failed for tenant={} subject={}: {}",
                        tenantId, dataSubjectId, e.getMessage());
            }
        }

        DataSubjectExport export = new DataSubjectExport(
                tenantId, dataSubjectId, fmt, clock.instant(),
                transcripts, List.of(), auditEvents, List.of());
        log.info("Exported subject {} for tenant {} ({}): totalRecords={}",
                dataSubjectId, tenantId, fmt, export.totalRecords());
        return export;
    }

    private Map<String, Object> toTranscriptMap(TranscriptSession s, ExportFormat fmt) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (fmt == ExportFormat.JSON_LD) {
            m.put("@type", "https://schema.org/Conversation");
            m.put("@context", "https://schema.org");
        }
        m.put("sessionId", s.sessionId());
        m.put("tenantId", s.tenantId());
        m.put("agentId", s.agentId());
        m.put("channel", s.channel());
        m.put("startTime", s.startTime() == null ? null : s.startTime().toString());
        m.put("utteranceCount", s.utterances() == null ? 0 : s.utterances().size());
        return m;
    }

    private Map<String, Object> toAuditMap(AuditEvent e, ExportFormat fmt) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (fmt == ExportFormat.JSON_LD) {
            m.put("@type", "https://w3.org/ns/dpv#PersonalDataHandling");
            m.put("@context", "https://w3.org/ns/dpv");
        }
        m.put("id", e.id());
        m.put("timestamp", e.timestamp() == null ? null : e.timestamp().toString());
        m.put("action", e.action());
        m.put("resource", e.resource());
        m.put("outcome", e.outcome() == null ? null : e.outcome().name());
        m.put("lawfulBasis", e.lawfulBasis());
        m.put("dataCategories", e.dataCategories());
        m.put("recipients", e.recipients());
        m.put("retentionDays", e.retentionDays());
        m.put("consentToken", e.consentToken());
        return m;
    }

    private static Instant nowFromClock(Clock c) { return c.instant(); }
}
