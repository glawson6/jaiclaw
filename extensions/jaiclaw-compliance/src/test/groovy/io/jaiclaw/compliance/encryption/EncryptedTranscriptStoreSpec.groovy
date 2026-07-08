package io.jaiclaw.compliance.encryption

import io.jaiclaw.audit.TranscriptSession
import io.jaiclaw.audit.TranscriptStore
import io.jaiclaw.audit.TranscriptUtterance
import io.jaiclaw.core.encryption.FieldEncryptor
import spock.lang.Specification

import java.time.Instant

class EncryptedTranscriptStoreSpec extends Specification {

    FieldEncryptor encryptor = new AesGcmFieldEncryptor(AesGcmFieldEncryptor.generateKey())

    def "content is encrypted at rest and decrypted on load"() {
        given:
        Map<String, TranscriptSession> backing = [:]
        TranscriptStore inner = new TranscriptStore() {
            @Override void save(TranscriptSession s) { backing[s.sessionId()] = s }
            @Override Optional<TranscriptSession> load(String id) { Optional.ofNullable(backing[id]) }
            @Override List<String> list(String t, int l) { new ArrayList<>(backing.keySet()) }
            @Override boolean delete(String id) { backing.remove(id) != null }
        }
        def store = new EncryptedTranscriptStore(inner, encryptor)
        def session = TranscriptSession.builder()
                .sessionId("s1").tenantId("acme").agentId("a").channel("slack")
                .startTime(Instant.parse("2026-01-01T00:00:00Z"))
                .utterances([TranscriptUtterance.user("PLAINTEXT-SECRET")]).build()

        when:
        store.save(session)

        then: "stored content is NOT plaintext"
        !backing["s1"].utterances().first().content().contains("PLAINTEXT-SECRET")

        when:
        Optional<TranscriptSession> loaded = store.load("s1")

        then: "loaded content round-trips to plaintext"
        loaded.isPresent()
        loaded.get().utterances().first().content() == "PLAINTEXT-SECRET"
    }

    def "metadata (session id, tenant id, timestamps) stays in clear so queries still work"() {
        given:
        Map<String, TranscriptSession> backing = [:]
        TranscriptStore inner = new TranscriptStore() {
            @Override void save(TranscriptSession s) { backing[s.sessionId()] = s }
            @Override Optional<TranscriptSession> load(String id) { Optional.ofNullable(backing[id]) }
            @Override List<String> list(String t, int l) { new ArrayList<>(backing.keySet()) }
            @Override boolean delete(String id) { backing.remove(id) != null }
        }
        def store = new EncryptedTranscriptStore(inner, encryptor)
        def session = TranscriptSession.builder()
                .sessionId("s1").tenantId("acme").agentId("agentA").channel("slack")
                .startTime(Instant.parse("2026-01-01T00:00:00Z"))
                .utterances([TranscriptUtterance.user("hi")]).build()

        when:
        store.save(session)
        def stored = backing["s1"]

        then:
        stored.sessionId() == "s1"
        stored.tenantId() == "acme"
        stored.agentId() == "agentA"
        stored.channel() == "slack"
        stored.startTime() == Instant.parse("2026-01-01T00:00:00Z")
    }
}
