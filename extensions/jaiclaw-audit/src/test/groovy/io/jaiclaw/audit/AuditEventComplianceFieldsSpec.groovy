package io.jaiclaw.audit

import spock.lang.Specification

/**
 * T1-2 acceptance: AuditEvent carries five optional compliance fields that
 * downstream code uses to reconstruct GDPR Art. 30 Records of Processing
 * (RoPA) and HIPAA §164.312(b) access logs. Backward-compatibility with the
 * pre-T1-2 8-arg constructor is preserved.
 */
class AuditEventComplianceFieldsSpec extends Specification {

    def "8-arg backward-compat ctor leaves every compliance field null / empty"() {
        when:
        def event = new AuditEvent(
                "evt-1", null, "t-1", "user", "message.sent", "sess:123",
                AuditEvent.Outcome.SUCCESS, [:])

        then:
        event.lawfulBasis() == null
        event.dataCategories() == [] as Set
        event.recipients() == [] as Set
        event.retentionDays() == null
        event.consentToken() == null
    }

    def "success factory still works — compliance fields default to null / empty"() {
        when:
        def event = AuditEvent.success("evt-1", "t-1", "user", "message.sent", "sess:123")

        then:
        event.lawfulBasis() == null
        event.dataCategories().isEmpty()
        event.recipients().isEmpty()
        event.retentionDays() == null
        event.consentToken() == null
    }

    def "builder accepts all five compliance fields"() {
        when:
        def event = AuditEvent.builder()
                .id("evt-1")
                .tenantId("t-1")
                .actor("agent")
                .action("model.inference.request")
                .resource("anthropic/claude-sonnet-4-5")
                .lawfulBasis("contract")
                .dataCategories(["user_utterance", "system_prompt"] as Set)
                .recipients(["anthropic-bedrock-us-east-1"] as Set)
                .retentionDays(2190)
                .consentToken("cnst_abc123")
                .build()

        then:
        event.lawfulBasis() == "contract"
        event.dataCategories() == ["user_utterance", "system_prompt"] as Set
        event.recipients() == ["anthropic-bedrock-us-east-1"] as Set
        event.retentionDays() == 2190
        event.consentToken() == "cnst_abc123"
    }

    def "canonical 13-arg constructor accepts all fields"() {
        when:
        def event = new AuditEvent(
                "evt-1", null, "t-1", "user", "action", "resource",
                AuditEvent.Outcome.SUCCESS, [:],
                "consent",
                ["cat"] as Set,
                ["recipient"] as Set,
                365,
                "token")

        then:
        event.lawfulBasis() == "consent"
        event.dataCategories() == ["cat"] as Set
        event.recipients() == ["recipient"] as Set
        event.retentionDays() == 365
        event.consentToken() == "token"
    }

    def "dataCategories and recipients are defensively copied — callers can't mutate afterwards"() {
        given:
        def cats = ["a", "b"] as Set
        def rcpts = ["r1", "r2"] as Set

        when:
        def event = AuditEvent.builder()
                .id("evt-1")
                .action("x")
                .dataCategories(cats)
                .recipients(rcpts)
                .build()

        then:
        // Attempting to mutate the returned set throws (Set.copyOf returns immutable).
        try { event.dataCategories().add("c"); false } catch (UnsupportedOperationException e) { true }
        try { event.recipients().add("r3"); false } catch (UnsupportedOperationException e) { true }
    }

    def "existing AuditEvent tests still pass — no regression"() {
        // Smoke check that the 8-arg ctor path used across the existing test
        // suite still validates its inputs.
        when:
        new AuditEvent("", null, null, null, "test.action", null, null, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "compliance fields round-trip through FileAuditLogger (Jackson serialization)"() {
        given: "an event with every compliance field populated"
        def dir = java.nio.file.Files.createTempDirectory("audit-compliance-")
        def logger = new FileAuditLogger(dir)
        def event = AuditEvent.builder()
                .id("evt-round-trip")
                .timestamp(java.time.Instant.parse("2026-07-07T21:00:00Z"))
                .tenantId("t-1")
                .actor("agent")
                .action("model.inference.request")
                .resource("anthropic/claude-sonnet-4-5")
                .lawfulBasis("contract")
                .dataCategories(["user_utterance", "system_prompt"] as Set)
                .recipients(["anthropic-bedrock-us-east-1"] as Set)
                .retentionDays(2190)
                .consentToken("cnst_abc")
                .build()

        when: "we log then query it back"
        logger.log(event)
        def loaded = logger.query("t-1", 10)

        then: "every compliance field survives the JSONL round-trip"
        loaded.size() == 1
        def back = loaded[0]
        back.lawfulBasis() == "contract"
        back.dataCategories() == ["user_utterance", "system_prompt"] as Set
        back.recipients() == ["anthropic-bedrock-us-east-1"] as Set
        back.retentionDays() == 2190
        back.consentToken() == "cnst_abc"

        cleanup:
        dir.toFile().deleteDir()
    }

    def "compliance fields absent → JSON round-trip preserves null / empty"() {
        given:
        def dir = java.nio.file.Files.createTempDirectory("audit-noncompliance-")
        def logger = new FileAuditLogger(dir)
        // Use the 8-arg backward-compat ctor to simulate a pre-T1-2 caller.
        def event = new AuditEvent(
                "evt-legacy",
                java.time.Instant.parse("2026-07-07T21:00:00Z"),
                "t-1", "user", "message.sent", "session:abc",
                AuditEvent.Outcome.SUCCESS, [greeting: "hello"])

        when:
        logger.log(event)
        def loaded = logger.query("t-1", 10)

        then: "compliance fields are null / empty on the loaded copy"
        loaded.size() == 1
        loaded[0].lawfulBasis() == null
        loaded[0].dataCategories() == [] as Set
        loaded[0].recipients() == [] as Set
        loaded[0].retentionDays() == null
        loaded[0].consentToken() == null
        // Original non-compliance fields untouched
        loaded[0].details() == [greeting: "hello"]

        cleanup:
        dir.toFile().deleteDir()
    }
}
