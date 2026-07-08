package io.jaiclaw.compliance.gdpr

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.AuditLogger
import io.jaiclaw.audit.TranscriptSession
import io.jaiclaw.audit.TranscriptStore
import io.jaiclaw.core.gdpr.DataSubjectErasureSpi
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AggregateDataSubjectErasureSpiSpec extends Specification {

    Clock fixed = Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneOffset.UTC)

    def "erasure fans out to every transcript store and audit logger"() {
        given:
        TranscriptStore store1 = Mock()
        TranscriptStore store2 = Mock()
        AuditLogger logger = Mock()
        def spi = new AggregateDataSubjectErasureSpi([store1, store2], [logger], fixed)

        when:
        DataSubjectErasureSpi.ErasureResult result = spi.eraseForDataSubject(
                "acme", "user-42", DataSubjectErasureSpi.ErasureReason.ART_17_REQUEST)

        then:
        1 * store1.eraseForDataSubject("acme", "user-42") >> 3
        1 * store2.eraseForDataSubject("acme", "user-42") >> 1
        1 * logger.eraseForDataSubject("acme", "user-42") >> 5
        1 * logger.log(_ as AuditEvent)
        result.transcriptsDeleted() == 4
        result.auditEventsDeleted() == 5
        result.totalDeleted() == 9
    }

    def "erasure emits an audit event with reason + counts"() {
        given:
        TranscriptStore store = Mock() { eraseForDataSubject(_, _) >> 2 }
        AuditLogger logger = Mock() { eraseForDataSubject(_, _) >> 4 }
        def spi = new AggregateDataSubjectErasureSpi([store], [logger], fixed)
        AuditEvent captured = null

        when:
        spi.eraseForDataSubject("acme", "user-42",
                DataSubjectErasureSpi.ErasureReason.CONSENT_WITHDRAWAL)

        then:
        1 * logger.log(_ as AuditEvent) >> { AuditEvent evt -> captured = evt }
        captured.action() == AggregateDataSubjectErasureSpi.ACTION_ERASURE
        captured.tenantId() == "acme"
        captured.resource() == "subject:user-42"
        captured.details()["reason"] == "CONSENT_WITHDRAWAL"
        captured.details()["transcriptsDeleted"] == 2
        captured.details()["auditEventsDeleted"] == 4
    }

    def "erasure never crosses tenant boundaries"() {
        given:
        TranscriptStore store = Mock()
        AuditLogger logger = Mock()
        def spi = new AggregateDataSubjectErasureSpi([store], [logger], fixed)

        when:
        spi.eraseForDataSubject("acme", "user-42",
                DataSubjectErasureSpi.ErasureReason.OPERATOR_INITIATED)

        then:
        1 * store.eraseForDataSubject("acme", "user-42") >> 0
        0 * store.eraseForDataSubject("beta", _)
        1 * logger.eraseForDataSubject("acme", "user-42") >> 0
        1 * logger.log(_ as AuditEvent)
    }

    def "erasure of a store that throws is isolated — other stores still run"() {
        given:
        TranscriptStore healthy = Mock()
        TranscriptStore broken = Mock()
        AuditLogger logger = Mock()
        def spi = new AggregateDataSubjectErasureSpi([broken, healthy], [logger], fixed)

        when:
        DataSubjectErasureSpi.ErasureResult result = spi.eraseForDataSubject(
                "acme", "user-42", DataSubjectErasureSpi.ErasureReason.ART_17_REQUEST)

        then:
        1 * broken.eraseForDataSubject("acme", "user-42") >> { throw new RuntimeException("boom") }
        1 * healthy.eraseForDataSubject("acme", "user-42") >> 2
        1 * logger.eraseForDataSubject("acme", "user-42") >> 0
        1 * logger.log(_ as AuditEvent)
        result.transcriptsDeleted() == 2
    }

    def "blank tenantId / subjectId is rejected"() {
        given:
        def spi = new AggregateDataSubjectErasureSpi([], [], fixed)

        when:
        spi.eraseForDataSubject(tenant, subject, DataSubjectErasureSpi.ErasureReason.ART_17_REQUEST)

        then:
        thrown(IllegalArgumentException)

        where:
        tenant | subject
        null   | "user"
        ""     | "user"
        "acme" | null
        "acme" | ""
    }

    def "transcript store default eraseForDataSubject removes sessions ending with :subjectId"() {
        given:
        Map<String, TranscriptSession> data = [
                "agent1:slack:chan1:user-42" : session("agent1:slack:chan1:user-42", "acme"),
                "agent1:slack:chan1:user-99" : session("agent1:slack:chan1:user-99", "acme"),
                "agent1:email:mailbox:user-42" : session("agent1:email:mailbox:user-42", "acme"),
        ]
        def store = new TranscriptStore() {
            @Override void save(TranscriptSession s) {}
            @Override Optional<TranscriptSession> load(String id) { Optional.ofNullable(data[id]) }
            @Override List<String> list(String tenantId, int limit) { new ArrayList<>(data.keySet()) }
            @Override boolean delete(String id) { data.remove(id) != null }
        }

        when:
        int removed = store.eraseForDataSubject("acme", "user-42")

        then:
        removed == 2
        data.keySet() == ["agent1:slack:chan1:user-99"] as Set
    }

    private TranscriptSession session(String id, String tenantId) {
        TranscriptSession.builder()
                .sessionId(id)
                .tenantId(tenantId)
                .agentId("agent1")
                .channel("slack")
                .startTime(Instant.parse("2026-01-01T00:00:00Z"))
                .build()
    }
}
