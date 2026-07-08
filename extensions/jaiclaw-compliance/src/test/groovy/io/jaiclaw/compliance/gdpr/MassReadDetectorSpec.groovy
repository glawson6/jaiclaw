package io.jaiclaw.compliance.gdpr

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.AuditLogger
import io.jaiclaw.core.gdpr.AnomalyDetector
import spock.lang.Specification

import java.time.Instant

class MassReadDetectorSpec extends Specification {

    def "flags actors that exceed the distinct-subject threshold"() {
        given:
        List<AuditEvent> events = []
        (1..5).each { events.add(event("e${it}", "user-scanner", "data.read", "subject-${it}", "2026-07-01T12:00:00Z")) }
        events.add(event("e6", "user-normal", "data.read", "subject-99", "2026-07-01T12:00:00Z"))
        events.add(event("e7", "user-scanner", "unrelated.action", "x", "2026-07-01T12:00:00Z"))
        AuditLogger logger = Mock()
        List<AuditEvent> emitted = []
        1 * logger.query("acme", _) >> events
        _ * logger.log(_ as AuditEvent) >> { AuditEvent e -> emitted.add(e) }

        def detector = new MassReadDetector([logger], 5)

        when:
        List<AnomalyDetector.Anomaly> anomalies = detector.detect(
                "acme", Instant.parse("2026-06-30T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z"))

        then:
        anomalies.size() == 1
        anomalies[0].subject() == "user-scanner"
        anomalies[0].eventCount() == 5
        emitted.size() == 1
        emitted[0].action() == MassReadDetector.ACTION_SECURITY_EVENT
    }

    def "no anomalies when under threshold"() {
        given:
        List<AuditEvent> events = [event("e1", "user-x", "data.read", "s1", "2026-07-01T12:00:00Z")]
        AuditLogger logger = Mock()
        1 * logger.query("acme", _) >> events

        def detector = new MassReadDetector([logger], 10)

        expect:
        detector.detect("acme", null, null).isEmpty()
    }

    def "blank tenantId returns empty"() {
        given:
        AuditLogger logger = Mock()
        0 * logger.query(_, _)

        def detector = new MassReadDetector([logger], 1)

        expect:
        detector.detect(t, null, null).isEmpty()

        where:
        t << [null, ""]
    }

    private AuditEvent event(String id, String actor, String action, String resource, String ts) {
        AuditEvent.builder()
                .id(id).timestamp(Instant.parse(ts))
                .tenantId("acme").actor(actor).action(action).resource(resource)
                .outcome(AuditEvent.Outcome.SUCCESS).build()
    }
}
