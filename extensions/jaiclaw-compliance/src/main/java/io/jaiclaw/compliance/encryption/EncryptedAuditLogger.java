package io.jaiclaw.compliance.encryption;

import io.jaiclaw.audit.AuditEvent;
import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.core.encryption.FieldEncryptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * T2-4 — decorator that encrypts the {@code details} map of every
 * {@link AuditEvent} on write and decrypts on read.
 *
 * <p>Values in {@code details} are serialized to their {@code toString()} and
 * encrypted individually — this keeps the map shape identical between
 * plaintext and ciphertext views. Metadata (id, timestamp, tenantId, action,
 * outcome) stays in clear so queries and time-based purge work unchanged.
 */
public class EncryptedAuditLogger implements AuditLogger {

    /** Prefix marker so decryption can recognize its own encrypted values. */
    static final String ENC_PREFIX = "enc:";

    private final AuditLogger delegate;
    private final FieldEncryptor encryptor;

    public EncryptedAuditLogger(AuditLogger delegate, FieldEncryptor encryptor) {
        this.delegate = delegate;
        this.encryptor = encryptor;
    }

    @Override
    public void log(AuditEvent event) {
        delegate.log(encryptDetails(event));
    }

    @Override
    public List<AuditEvent> query(String tenantId, int limit) {
        return delegate.query(tenantId, limit).stream().map(this::decryptDetails).toList();
    }

    @Override
    public Optional<AuditEvent> findById(String id) {
        return delegate.findById(id).map(this::decryptDetails);
    }

    @Override
    public long count(String tenantId) {
        return delegate.count(tenantId);
    }

    @Override
    public int purgeOlderThan(String tenantId, Instant cutoff) {
        return delegate.purgeOlderThan(tenantId, cutoff);
    }

    @Override
    public int eraseForDataSubject(String tenantId, String dataSubjectId) {
        return delegate.eraseForDataSubject(tenantId, dataSubjectId);
    }

    private AuditEvent encryptDetails(AuditEvent e) {
        if (e == null || e.details() == null || e.details().isEmpty()) return e;
        Map<String, Object> ed = new HashMap<>();
        for (Map.Entry<String, Object> entry : e.details().entrySet()) {
            Object v = entry.getValue();
            if (v == null) { ed.put(entry.getKey(), null); continue; }
            ed.put(entry.getKey(), ENC_PREFIX + encryptor.encrypt(v.toString()));
        }
        return AuditEvent.builder()
                .id(e.id()).timestamp(e.timestamp()).tenantId(e.tenantId()).actor(e.actor())
                .action(e.action()).resource(e.resource()).outcome(e.outcome()).details(ed)
                .lawfulBasis(e.lawfulBasis()).dataCategories(e.dataCategories())
                .recipients(e.recipients()).retentionDays(e.retentionDays())
                .consentToken(e.consentToken()).build();
    }

    private AuditEvent decryptDetails(AuditEvent e) {
        if (e == null || e.details() == null || e.details().isEmpty()) return e;
        Map<String, Object> dd = new HashMap<>();
        for (Map.Entry<String, Object> entry : e.details().entrySet()) {
            Object v = entry.getValue();
            if (v instanceof String s && s.startsWith(ENC_PREFIX)) {
                dd.put(entry.getKey(), encryptor.decrypt(s.substring(ENC_PREFIX.length())));
            } else {
                dd.put(entry.getKey(), v);
            }
        }
        return AuditEvent.builder()
                .id(e.id()).timestamp(e.timestamp()).tenantId(e.tenantId()).actor(e.actor())
                .action(e.action()).resource(e.resource()).outcome(e.outcome()).details(dd)
                .lawfulBasis(e.lawfulBasis()).dataCategories(e.dataCategories())
                .recipients(e.recipients()).retentionDays(e.retentionDays())
                .consentToken(e.consentToken()).build();
    }
}
