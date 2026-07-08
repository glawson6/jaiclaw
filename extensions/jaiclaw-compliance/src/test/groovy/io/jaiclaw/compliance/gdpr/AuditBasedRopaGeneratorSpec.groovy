package io.jaiclaw.compliance.gdpr

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.AuditLogger
import io.jaiclaw.core.gdpr.RopaGenerator
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AuditBasedRopaGeneratorSpec extends Specification {

    Clock fixed = Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneOffset.UTC)

    def "generates activity groups from audit events in the window"() {
        given:
        AuditLogger logger = Mock() {
            query("acme", _) >> [
                    event("e1", "acme", "model.inference.request", "contract", ["user_utterance"], ["openai"], "2026-07-01T12:00:00Z"),
                    event("e2", "acme", "model.inference.request", "contract", ["user_utterance"], ["openai"], "2026-07-02T12:00:00Z"),
                    event("e3", "acme", "data.subject_erasure", "legal_obligation", null, null, "2026-07-03T12:00:00Z"),
                    event("e4", "beta", "model.inference.request", "contract", ["user_utterance"], ["anthropic"], "2026-07-04T12:00:00Z"),
            ]
        }
        def gen = new AuditBasedRopaGenerator([logger], fixed)

        when:
        RopaGenerator.Ropa ropa = gen.generate("acme", Instant.parse("2026-06-30T00:00:00Z"), Instant.parse("2026-07-04T00:00:00Z"))

        then:
        ropa.tenantId() == "acme"
        ropa.processorName() == AuditBasedRopaGenerator.PROCESSOR_NAME
        ropa.activities().size() == 2
        RopaGenerator.ProcessingActivity inf = ropa.activities().find { it.action() == "model.inference.request" }
        inf.eventCount() == 2
        inf.lawfulBases() == ["contract"]
        inf.recipients() == ["openai"]
        inf.dataCategories() == ["user_utterance"]
        RopaGenerator.ProcessingActivity er = ropa.activities().find { it.action() == "data.subject_erasure" }
        er.eventCount() == 1
        er.lawfulBases() == ["legal_obligation"]
    }

    def "defaults from = to - 30 days when unset"() {
        given:
        AuditLogger logger = Mock() { query(_, _) >> [] }
        def gen = new AuditBasedRopaGenerator([logger], fixed)

        when:
        RopaGenerator.Ropa ropa = gen.generate("acme", null, null)

        then:
        ropa.to() == Instant.parse("2026-07-07T12:00:00Z")
        ropa.from() == Instant.parse("2026-06-07T12:00:00Z")
    }

    def "blank tenantId is rejected"() {
        given:
        def gen = new AuditBasedRopaGenerator([], fixed)

        when:
        gen.generate(t, null, null)

        then:
        thrown(IllegalArgumentException)

        where:
        t << [null, ""]
    }

    private AuditEvent event(String id, String tenant, String action, String basis, List<String> cats, List<String> recips, String ts) {
        AuditEvent.builder()
                .id(id).timestamp(Instant.parse(ts))
                .tenantId(tenant).actor("system").action(action).resource("x")
                .outcome(AuditEvent.Outcome.SUCCESS)
                .lawfulBasis(basis)
                .dataCategories(cats == null ? null : cats as Set)
                .recipients(recips == null ? null : recips as Set)
                .build()
    }
}
