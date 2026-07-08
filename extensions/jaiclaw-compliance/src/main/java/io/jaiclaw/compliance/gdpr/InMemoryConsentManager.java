package io.jaiclaw.compliance.gdpr;

import io.jaiclaw.audit.AuditEvent;
import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.core.gdpr.ConsentManager;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T2-5 reference {@link ConsentManager} — in-memory store keyed by
 * {@code tenantId:dataSubjectId}. Adequate for tests + demo; production
 * adopters should implement a durable store (a {@code FileConsentStore}
 * or a database-backed impl).
 *
 * <p>Every consent + withdrawal emits a {@code consent.recorded} or
 * {@code consent.withdrawn} audit event on every registered
 * {@link AuditLogger}.
 */
public class InMemoryConsentManager implements ConsentManager {

    public static final String ACTION_RECORDED = "consent.recorded";
    public static final String ACTION_WITHDRAWN = "consent.withdrawn";

    /** Outer key: tenantId + ":" + dataSubjectId. Inner key: consentType. */
    private final Map<String, Map<String, ConsentEntry>> store = new ConcurrentHashMap<>();
    private final List<AuditLogger> auditLoggers;
    private final Clock clock;

    public InMemoryConsentManager(List<AuditLogger> auditLoggers) {
        this(auditLoggers, Clock.systemUTC());
    }

    public InMemoryConsentManager(List<AuditLogger> auditLoggers, Clock clock) {
        this.auditLoggers = List.copyOf(auditLoggers == null ? List.of() : auditLoggers);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public ConsentToken recordConsent(String tenantId, String dataSubjectId, String consentType, String proof) {
        validate(tenantId, dataSubjectId, consentType);
        String key = key(tenantId, dataSubjectId);
        String token = UUID.randomUUID().toString();
        Instant now = clock.instant();
        ConsentEntry entry = new ConsentEntry(token, consentType, now, proof, true);
        store.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(consentType, entry);
        writeAudit(ACTION_RECORDED, tenantId, dataSubjectId, consentType, token);
        return new ConsentToken(token, tenantId, dataSubjectId, consentType, now);
    }

    @Override
    public void withdrawConsent(String tenantId, String dataSubjectId, String consentType) {
        validate(tenantId, dataSubjectId, consentType);
        String key = key(tenantId, dataSubjectId);
        Map<String, ConsentEntry> tenantStore = store.get(key);
        String previousToken = null;
        if (tenantStore != null) {
            ConsentEntry existing = tenantStore.get(consentType);
            if (existing != null) previousToken = existing.token;
            tenantStore.remove(consentType);
        }
        writeAudit(ACTION_WITHDRAWN, tenantId, dataSubjectId, consentType, previousToken);
    }

    @Override
    public Map<String, Instant> getConsentStatus(String tenantId, String dataSubjectId) {
        if (tenantId == null || dataSubjectId == null) return Map.of();
        Map<String, ConsentEntry> tenantStore = store.get(key(tenantId, dataSubjectId));
        if (tenantStore == null || tenantStore.isEmpty()) return Map.of();
        Map<String, Instant> out = new LinkedHashMap<>();
        tenantStore.values().stream()
                .filter(e -> e.active)
                .forEach(e -> out.put(e.consentType, e.recordedAt));
        return Map.copyOf(out);
    }

    private void writeAudit(String action, String tenantId, String dataSubjectId,
                            String consentType, String token) {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(clock.instant())
                .tenantId(tenantId)
                .actor("system")
                .action(action)
                .resource("subject:" + dataSubjectId)
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details(Map.of("consentType", consentType, "token", token == null ? "" : token))
                .consentToken(token)
                .build();
        for (AuditLogger logger : auditLoggers) {
            try {
                logger.log(event);
            } catch (RuntimeException e) {
                // Consent recording must never fail because of a broken audit sink.
            }
        }
    }

    private static void validate(String tenantId, String dataSubjectId, String consentType) {
        if (tenantId == null || tenantId.isBlank())
            throw new IllegalArgumentException("tenantId must not be blank");
        if (dataSubjectId == null || dataSubjectId.isBlank())
            throw new IllegalArgumentException("dataSubjectId must not be blank");
        if (consentType == null || consentType.isBlank())
            throw new IllegalArgumentException("consentType must not be blank");
    }

    private static String key(String tenantId, String dataSubjectId) {
        return tenantId + ":" + dataSubjectId;
    }

    private record ConsentEntry(String token, String consentType, Instant recordedAt,
                                 String proof, boolean active) {}
}
