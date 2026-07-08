package io.jaiclaw.compliance.encryption

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.AuditLogger
import io.jaiclaw.core.encryption.FieldEncryptor
import spock.lang.Specification

import java.time.Instant

class EncryptedAuditLoggerSpec extends Specification {

    FieldEncryptor encryptor = new AesGcmFieldEncryptor(AesGcmFieldEncryptor.generateKey())

    def "details values are encrypted at rest and decrypted on read"() {
        given:
        List<AuditEvent> backing = []
        AuditLogger inner = new AuditLogger() {
            @Override void log(AuditEvent e) { backing.add(e) }
            @Override List<AuditEvent> query(String t, int l) { new ArrayList<>(backing) }
            @Override Optional<AuditEvent> findById(String id) { backing.find { it.id() == id } ? Optional.of(backing.find { it.id() == id }) : Optional.empty() }
            @Override long count(String t) { backing.size() }
        }
        def logger = new EncryptedAuditLogger(inner, encryptor)
        def evt = AuditEvent.builder()
                .id("e1").timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .tenantId("acme").actor("system").action("test").resource("x")
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details(["prompt": "SECRET-PROMPT", "count": 42])
                .build()

        when:
        logger.log(evt)

        then: "stored details values do not contain the plaintext"
        !backing[0].details()["prompt"].toString().contains("SECRET-PROMPT")
        backing[0].details()["prompt"].toString().startsWith("enc:")
        backing[0].details()["count"].toString().startsWith("enc:")

        when:
        List<AuditEvent> read = logger.query("acme", 10)

        then: "reading decrypts the details values"
        read[0].details()["prompt"] == "SECRET-PROMPT"
        read[0].details()["count"] == "42"
    }

    def "metadata (id, timestamp, tenantId, action, outcome) stays in clear"() {
        given:
        List<AuditEvent> backing = []
        AuditLogger inner = Stub() {
            log(_) >> { AuditEvent e -> backing.add(e) }
        }
        def logger = new EncryptedAuditLogger(inner, encryptor)
        def evt = AuditEvent.builder()
                .id("evt-42").timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .tenantId("acme").actor("agentA").action("model.inference.request").resource("openai")
                .outcome(AuditEvent.Outcome.SUCCESS).details(["v": "x"]).build()

        when:
        logger.log(evt)

        then:
        backing[0].id() == "evt-42"
        backing[0].tenantId() == "acme"
        backing[0].actor() == "agentA"
        backing[0].action() == "model.inference.request"
        backing[0].resource() == "openai"
    }

    def "empty details map is a no-op — event passes through unchanged"() {
        given:
        List<AuditEvent> backing = []
        AuditLogger inner = Stub() {
            log(_) >> { AuditEvent e -> backing.add(e) }
        }
        def logger = new EncryptedAuditLogger(inner, encryptor)
        def evt = AuditEvent.builder()
                .id("e2").timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .tenantId("acme").actor("system").action("test").resource("x")
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details([:]).build()

        when:
        logger.log(evt)

        then:
        backing[0].details().isEmpty()
    }

    def "count + purge + erase delegate through without decryption"() {
        given:
        AuditLogger inner = Mock()
        def logger = new EncryptedAuditLogger(inner, encryptor)

        when:
        logger.count("acme")
        logger.purgeOlderThan("acme", Instant.parse("2026-01-01T00:00:00Z"))
        logger.eraseForDataSubject("acme", "user-42")

        then:
        1 * inner.count("acme") >> 3
        1 * inner.purgeOlderThan("acme", _) >> 5
        1 * inner.eraseForDataSubject("acme", "user-42") >> 7
    }

    def "findById round-trips through decryption"() {
        given:
        List<AuditEvent> backing = []
        AuditLogger inner = new AuditLogger() {
            @Override void log(AuditEvent e) { backing.add(e) }
            @Override List<AuditEvent> query(String t, int l) { new ArrayList<>(backing) }
            @Override Optional<AuditEvent> findById(String id) { backing.find { it.id() == id } ? Optional.of(backing.find { it.id() == id }) : Optional.empty() }
            @Override long count(String t) { backing.size() }
        }
        def logger = new EncryptedAuditLogger(inner, encryptor)
        def evt = AuditEvent.builder()
                .id("e3").timestamp(Instant.now()).tenantId("acme")
                .actor("system").action("test").resource("x")
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details(["k": "SECRET-VALUE"]).build()
        logger.log(evt)

        when:
        Optional<AuditEvent> found = logger.findById("e3")

        then:
        found.isPresent()
        found.get().details()["k"] == "SECRET-VALUE"
    }
}
