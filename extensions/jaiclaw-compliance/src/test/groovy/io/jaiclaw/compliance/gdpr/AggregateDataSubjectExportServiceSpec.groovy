package io.jaiclaw.compliance.gdpr

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.AuditLogger
import io.jaiclaw.audit.TranscriptSession
import io.jaiclaw.audit.TranscriptStore
import io.jaiclaw.audit.TranscriptUtterance
import io.jaiclaw.core.gdpr.DataSubjectExportService
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AggregateDataSubjectExportServiceSpec extends Specification {

    Clock fixed = Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneOffset.UTC)

    def "export gathers transcripts + audit events for a subject"() {
        given:
        TranscriptStore store = Mock()
        AuditLogger logger = Mock()
        def svc = new AggregateDataSubjectExportService([store], [logger], fixed)

        when:
        DataSubjectExportService.DataSubjectExport export = svc.exportForDataSubject(
                "acme", "user-42", DataSubjectExportService.ExportFormat.JSON)

        then:
        1 * store.list("acme", _) >> [
                "agent1:slack:chan1:user-42",
                "agent1:slack:chan1:user-99",
                "user-42",
                "unrelated:session"
        ]
        1 * store.load("agent1:slack:chan1:user-42") >> Optional.of(session("agent1:slack:chan1:user-42", "acme"))
        1 * store.load("user-42") >> Optional.of(session("user-42", "acme"))
        0 * store.load("unrelated:session")
        0 * store.load("agent1:slack:chan1:user-99")
        1 * logger.query("acme", _) >> [
                event("evt-1", "acme", "session:agent1:slack:chan1:user-42"),
                event("evt-2", "acme", "session:agent1:slack:chan1:user-99"),
                event("evt-3", "acme", "user-42")
        ]
        export.transcripts().size() == 2
        export.auditEvents().size() == 2
        export.totalRecords() == 4
        export.tenantId() == "acme"
        export.dataSubjectId() == "user-42"
    }

    def "JSON-LD format annotates records with schema.org / DPV context"() {
        given:
        TranscriptStore store = Mock() {
            list(_, _) >> ["s:user-42"]
            load("s:user-42") >> Optional.of(session("s:user-42", "acme"))
        }
        AuditLogger logger = Mock() { query(_, _) >> [] }
        def svc = new AggregateDataSubjectExportService([store], [logger], fixed)

        when:
        DataSubjectExportService.DataSubjectExport export = svc.exportForDataSubject(
                "acme", "user-42", DataSubjectExportService.ExportFormat.JSON_LD)

        then:
        export.transcripts().first()["@type"] == "https://schema.org/Conversation"
        export.transcripts().first()["@context"] == "https://schema.org"
    }

    def "export refuses blank tenant / subject"() {
        given:
        def svc = new AggregateDataSubjectExportService([], [], fixed)

        when:
        svc.exportForDataSubject(t, s, DataSubjectExportService.ExportFormat.JSON)

        then:
        thrown(IllegalArgumentException)

        where:
        t      | s
        null   | "x"
        "acme" | null
        ""     | "x"
        "acme" | ""
    }

    private TranscriptSession session(String id, String tenantId) {
        TranscriptSession.builder()
                .sessionId(id).tenantId(tenantId).agentId("agent1").channel("slack")
                .startTime(Instant.parse("2026-01-01T00:00:00Z"))
                .utterances([new TranscriptUtterance("user", "hi", Instant.parse("2026-01-01T00:00:00Z"), [:])])
                .build()
    }

    private AuditEvent event(String id, String tenantId, String resource) {
        AuditEvent.builder()
                .id(id).timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .tenantId(tenantId).actor("system").action("session.started").resource(resource)
                .outcome(AuditEvent.Outcome.SUCCESS).build()
    }
}
