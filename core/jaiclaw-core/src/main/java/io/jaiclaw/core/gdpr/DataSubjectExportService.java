package io.jaiclaw.core.gdpr;

import io.jaiclaw.core.api.Stable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * T2-2 — GDPR Art. 15 (right of access) + Art. 20 (data portability) SPI.
 *
 * <p>Implementations gather every stored artifact for a data subject —
 * transcripts, memory rows, audit events, session metadata — and return a
 * portable bundle in the requested format.
 *
 * <p>Contract:
 * <ul>
 *   <li>Export MUST be tenant-scoped — tenant A cannot request tenant B's
 *       data even given the same {@code dataSubjectId}.</li>
 *   <li>The bundle SHOULD include a {@code generatedAt} + {@code format}
 *       header so a receiving system can validate freshness + shape.</li>
 *   <li>JSON_LD format binds fields to schema.org / W3C DPV vocabulary where
 *       possible (Art. 20 portability requirement).</li>
 * </ul>
 */
@Stable
public interface DataSubjectExportService {

    /**
     * Export every stored artifact for a data subject within a tenant.
     *
     * @param tenantId      the tenant that owns the subject's data (never null)
     * @param dataSubjectId the subject identifier (see
     *                      {@link DataSubjectErasureSpi} for the session-key
     *                      matching convention)
     * @param format        the requested serialization format
     * @return the bundle
     */
    DataSubjectExport exportForDataSubject(String tenantId, String dataSubjectId, ExportFormat format);

    /**
     * Serialization format the caller wants.
     */
    enum ExportFormat {
        /** Plain JSON — the default and most compact form. */
        JSON,
        /** JSON-LD with schema.org / DPV bindings — Art. 20 portability form. */
        JSON_LD,
        /** ZIP-of-CSVs — one CSV per data class, for spreadsheet import. */
        CSV_BUNDLE
    }

    /**
     * Structured export bundle.
     *
     * @param tenantId       tenant the subject belongs to
     * @param dataSubjectId  subject identifier
     * @param format         format the payload is serialized in
     * @param generatedAt    when the export was assembled
     * @param transcripts    list of transcript sessions (as maps for portability)
     * @param memoryEntries  list of memory rows
     * @param auditEvents    list of audit events
     * @param sessions       list of active/closed session metadata
     */
    record DataSubjectExport(
            String tenantId,
            String dataSubjectId,
            ExportFormat format,
            Instant generatedAt,
            List<Map<String, Object>> transcripts,
            List<Map<String, Object>> memoryEntries,
            List<Map<String, Object>> auditEvents,
            List<Map<String, Object>> sessions
    ) {
        public DataSubjectExport {
            if (tenantId == null || tenantId.isBlank())
                throw new IllegalArgumentException("tenantId must not be blank");
            if (dataSubjectId == null || dataSubjectId.isBlank())
                throw new IllegalArgumentException("dataSubjectId must not be blank");
            if (format == null) format = ExportFormat.JSON;
            if (generatedAt == null) generatedAt = Instant.now();
            transcripts = transcripts == null ? List.of() : List.copyOf(transcripts);
            memoryEntries = memoryEntries == null ? List.of() : List.copyOf(memoryEntries);
            auditEvents = auditEvents == null ? List.of() : List.copyOf(auditEvents);
            sessions = sessions == null ? List.of() : List.copyOf(sessions);
        }

        public int totalRecords() {
            return transcripts.size() + memoryEntries.size() + auditEvents.size() + sessions.size();
        }
    }
}
