package io.jaiclaw.compliance.gdpr;

import io.jaiclaw.audit.AuditEvent;
import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.core.gdpr.ObjectionService;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T3-2 reference {@link ObjectionService} — mirrors
 * {@link InMemoryConsentManager} in shape and audit behavior. Same production
 * disclaimer: adequate for tests / demo, adopters should implement a durable
 * store.
 */
public class InMemoryObjectionService implements ObjectionService {

    public static final String ACTION_RECORDED = "objection.recorded";
    public static final String ACTION_RESCINDED = "objection.rescinded";

    /** tenantId:dataSubjectId → (purpose → recordedAt). */
    private final Map<String, Map<String, Instant>> store = new ConcurrentHashMap<>();
    private final List<AuditLogger> auditLoggers;
    private final Clock clock;

    public InMemoryObjectionService(List<AuditLogger> auditLoggers) {
        this(auditLoggers, Clock.systemUTC());
    }

    public InMemoryObjectionService(List<AuditLogger> auditLoggers, Clock clock) {
        this.auditLoggers = List.copyOf(auditLoggers == null ? List.of() : auditLoggers);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public ObjectionToken recordObjection(String tenantId, String dataSubjectId,
                                           String processingPurpose, String proof) {
        validate(tenantId, dataSubjectId, processingPurpose);
        String key = key(tenantId, dataSubjectId);
        Instant now = clock.instant();
        store.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(processingPurpose, now);
        String token = UUID.randomUUID().toString();
        writeAudit(ACTION_RECORDED, tenantId, dataSubjectId, processingPurpose, token);
        return new ObjectionToken(token, tenantId, dataSubjectId, processingPurpose, now);
    }

    @Override
    public void rescindObjection(String tenantId, String dataSubjectId, String processingPurpose) {
        validate(tenantId, dataSubjectId, processingPurpose);
        String key = key(tenantId, dataSubjectId);
        Map<String, Instant> tenantStore = store.get(key);
        if (tenantStore != null) tenantStore.remove(processingPurpose);
        writeAudit(ACTION_RESCINDED, tenantId, dataSubjectId, processingPurpose, null);
    }

    @Override
    public boolean hasObjection(String tenantId, String dataSubjectId, String processingPurpose) {
        if (tenantId == null || dataSubjectId == null || processingPurpose == null) return false;
        Map<String, Instant> tenantStore = store.get(key(tenantId, dataSubjectId));
        return tenantStore != null && tenantStore.containsKey(processingPurpose);
    }

    @Override
    public Map<String, Instant> getObjections(String tenantId, String dataSubjectId) {
        if (tenantId == null || dataSubjectId == null) return Map.of();
        Map<String, Instant> tenantStore = store.get(key(tenantId, dataSubjectId));
        if (tenantStore == null || tenantStore.isEmpty()) return Map.of();
        return Map.copyOf(new LinkedHashMap<>(tenantStore));
    }

    private void writeAudit(String action, String tenantId, String dataSubjectId,
                            String processingPurpose, String token) {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(clock.instant())
                .tenantId(tenantId)
                .actor("system")
                .action(action)
                .resource("subject:" + dataSubjectId)
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details(Map.of("processingPurpose", processingPurpose,
                        "token", token == null ? "" : token))
                .build();
        for (AuditLogger logger : auditLoggers) {
            try {
                logger.log(event);
            } catch (RuntimeException ignored) {
                // Never fail the objection flow because of a broken audit sink.
            }
        }
    }

    private static void validate(String tenantId, String dataSubjectId, String purpose) {
        if (tenantId == null || tenantId.isBlank())
            throw new IllegalArgumentException("tenantId must not be blank");
        if (dataSubjectId == null || dataSubjectId.isBlank())
            throw new IllegalArgumentException("dataSubjectId must not be blank");
        if (purpose == null || purpose.isBlank())
            throw new IllegalArgumentException("processingPurpose must not be blank");
    }

    private static String key(String tenantId, String dataSubjectId) {
        return tenantId + ":" + dataSubjectId;
    }
}
