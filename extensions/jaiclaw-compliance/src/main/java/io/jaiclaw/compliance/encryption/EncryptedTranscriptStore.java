package io.jaiclaw.compliance.encryption;

import io.jaiclaw.audit.TranscriptSession;
import io.jaiclaw.audit.TranscriptStore;
import io.jaiclaw.audit.TranscriptUtterance;
import io.jaiclaw.core.encryption.FieldEncryptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * T2-4 — decorator that encrypts the {@code content} of every
 * {@link TranscriptUtterance} on save and decrypts it on load. Metadata
 * (session id, tenant id, timestamps, channel) stays in clear so queries
 * and purge logic keep working.
 *
 * <p>Applies transparently over any {@link TranscriptStore} impl. Round-trip
 * is O(utterance count) — memory-only, no additional I/O.
 */
public class EncryptedTranscriptStore implements TranscriptStore {

    private final TranscriptStore delegate;
    private final FieldEncryptor encryptor;

    public EncryptedTranscriptStore(TranscriptStore delegate, FieldEncryptor encryptor) {
        this.delegate = delegate;
        this.encryptor = encryptor;
    }

    @Override
    public void save(TranscriptSession session) {
        delegate.save(encryptContent(session));
    }

    @Override
    public Optional<TranscriptSession> load(String sessionId) {
        return delegate.load(sessionId).map(this::decryptContent);
    }

    @Override
    public List<String> list(String tenantId, int limit) {
        return delegate.list(tenantId, limit);
    }

    @Override
    public boolean delete(String sessionId) {
        return delegate.delete(sessionId);
    }

    @Override
    public int purgeOlderThan(String tenantId, Instant cutoff) {
        return delegate.purgeOlderThan(tenantId, cutoff);
    }

    @Override
    public int eraseForDataSubject(String tenantId, String dataSubjectId) {
        return delegate.eraseForDataSubject(tenantId, dataSubjectId);
    }

    private TranscriptSession encryptContent(TranscriptSession s) {
        if (s == null || s.utterances() == null || s.utterances().isEmpty()) return s;
        List<TranscriptUtterance> mapped = s.utterances().stream()
                .map(u -> new TranscriptUtterance(
                        u.role(), encryptor.encrypt(u.content()), u.timestamp(), u.metadata()))
                .toList();
        return new TranscriptSession(
                s.sessionId(), s.tenantId(), s.agentId(), s.channel(), s.startTime(), mapped);
    }

    private TranscriptSession decryptContent(TranscriptSession s) {
        if (s == null || s.utterances() == null || s.utterances().isEmpty()) return s;
        List<TranscriptUtterance> mapped = s.utterances().stream()
                .map(u -> new TranscriptUtterance(
                        u.role(), encryptor.decrypt(u.content()), u.timestamp(), u.metadata()))
                .toList();
        return new TranscriptSession(
                s.sessionId(), s.tenantId(), s.agentId(), s.channel(), s.startTime(), mapped);
    }
}
