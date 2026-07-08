package io.jaiclaw.core.data;

import java.time.Duration;

/**
 * T1-6: retention policy applied by {@code RetentionEnforcementService} to
 * purge stored transcripts, memory, and audit entries older than the TTL.
 *
 * <p>A null TTL means "keep forever" (framework default). Non-null TTL means
 * the enforcement service will act on entries older than that duration on
 * its next scheduled tick.
 *
 * <p>Compliance mapping:
 * <ul>
 *   <li>GDPR Art. 5(e) — "kept in a form which permits identification of data
 *       subjects for no longer than is necessary". Set per-tenant via
 *       {@code TenantContext.getRetentionDays()}.</li>
 *   <li>HIPAA §164.316(b)(2) — 6-year minimum audit retention. When a tenant
 *       is marked {@code phi_processing=true}, {@code auditTtl} should be
 *       ≥ 2190 days.</li>
 * </ul>
 *
 * @param transcriptTtl TTL for {@link io.jaiclaw.core.agent.MemoryProvider} /
 *                       TranscriptStore session artifacts. Null = unlimited.
 * @param memoryTtl     TTL for workspace / long-term memory entries. Null = unlimited.
 * @param auditTtl      TTL for audit log entries. Null = unlimited.
 * @param onExpiry      what to do when an entry expires — {@link Action#DELETE}
 *                       (hard delete), {@link Action#ANONYMIZE} (redact PII,
 *                       keep the shell), or {@link Action#ARCHIVE} (move to
 *                       cold storage — deferred to 1.0). Default DELETE.
 */
public record RetentionPolicy(
        Duration transcriptTtl,
        Duration memoryTtl,
        Duration auditTtl,
        Action onExpiry
) {

    public enum Action { DELETE, ANONYMIZE, ARCHIVE }

    public RetentionPolicy {
        if (onExpiry == null) onExpiry = Action.DELETE;
    }

    /** Convenience: unlimited retention across all stores. */
    public static RetentionPolicy unlimited() {
        return new RetentionPolicy(null, null, null, Action.DELETE);
    }

    /**
     * Convenience: uniform TTL across every store. Useful when a tenant
     * declares a single {@code retention_days} and the deployer doesn't
     * need per-store granularity.
     */
    public static RetentionPolicy uniform(int days) {
        if (days <= 0) return unlimited();
        Duration ttl = Duration.ofDays(days);
        return new RetentionPolicy(ttl, ttl, ttl, Action.DELETE);
    }

    /**
     * True when NO store has a retention limit. Used by the enforcement
     * service to short-circuit tenants without a policy.
     */
    public boolean isUnlimited() {
        return transcriptTtl == null && memoryTtl == null && auditTtl == null;
    }
}
