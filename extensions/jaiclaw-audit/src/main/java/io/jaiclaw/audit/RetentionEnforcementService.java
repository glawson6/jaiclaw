package io.jaiclaw.audit;

import io.jaiclaw.core.data.RetentionPolicy;
import io.jaiclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/**
 * T1-6: enforce data-retention policies across {@link TranscriptStore} and
 * {@link AuditLogger}. Deployers wire this as a bean (or via the auto-config)
 * and either call {@link #enforceForTenant} on their own schedule or rely on
 * the auto-config to fire a daily {@code @Scheduled} tick.
 *
 * <p>Policy resolution is deliberately explicit — the enforcement service
 * takes a {@link TenantContext} + {@link RetentionPolicy} pair from the
 * caller. This keeps the service testable and lets a deployer wire multiple
 * policy sources (per-tenant metadata, global defaults, per-store overrides)
 * outside the service.
 *
 * <p>Every purge emits an audit event ({@code data.retention_purge}) so the
 * retention actions themselves are traceable for regulator reviews.
 */
public class RetentionEnforcementService {

    private static final Logger log = LoggerFactory.getLogger(RetentionEnforcementService.class);

    private final Collection<TranscriptStore> transcriptStores;
    private final Collection<AuditLogger> auditLoggers;
    private final Clock clock;

    public RetentionEnforcementService(Collection<TranscriptStore> transcriptStores,
                                        Collection<AuditLogger> auditLoggers) {
        this(transcriptStores, auditLoggers, Clock.systemUTC());
    }

    /** Ctor with an injectable clock for testability. */
    public RetentionEnforcementService(Collection<TranscriptStore> transcriptStores,
                                        Collection<AuditLogger> auditLoggers,
                                        Clock clock) {
        this.transcriptStores = transcriptStores;
        this.auditLoggers = auditLoggers;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Enforce a policy for a specific tenant. Walks every registered
     * TranscriptStore + AuditLogger, purges entries older than the
     * per-store TTL, and emits a {@code data.retention_purge} audit event
     * with the counts for observability.
     *
     * @param tenant the tenant whose data is subject to the policy (id used for scoping)
     * @param policy the policy to apply; {@link RetentionPolicy#isUnlimited()} short-circuits
     * @return a summary of counts removed per store category
     */
    public PurgeResult enforceForTenant(TenantContext tenant, RetentionPolicy policy) {
        if (tenant == null || tenant.getTenantId() == null) {
            log.warn("Skipping retention enforcement — tenant or tenantId is null");
            return PurgeResult.empty();
        }
        if (policy == null || policy.isUnlimited()) {
            return PurgeResult.empty();
        }
        Instant now = clock.instant();
        int transcriptsRemoved = 0;
        int auditRemoved = 0;

        // Transcripts
        if (policy.transcriptTtl() != null) {
            Instant cutoff = now.minus(policy.transcriptTtl());
            for (TranscriptStore store : transcriptStores) {
                try {
                    transcriptsRemoved += store.purgeOlderThan(tenant.getTenantId(), cutoff);
                } catch (RuntimeException e) {
                    log.warn("TranscriptStore purge failed for tenant {}: {}",
                            tenant.getTenantId(), e.getMessage());
                }
            }
        }

        // Audit
        if (policy.auditTtl() != null) {
            Instant cutoff = now.minus(policy.auditTtl());
            for (AuditLogger logger : auditLoggers) {
                try {
                    auditRemoved += logger.purgeOlderThan(tenant.getTenantId(), cutoff);
                } catch (RuntimeException e) {
                    log.warn("AuditLogger purge failed for tenant {}: {}",
                            tenant.getTenantId(), e.getMessage());
                }
            }
        }

        PurgeResult result = new PurgeResult(transcriptsRemoved, auditRemoved,
                Duration.between(now, clock.instant()));

        // Self-log via the same audit stream so retention actions are auditable.
        if (result.transcriptsRemoved() > 0 || result.auditEventsRemoved() > 0) {
            emitAuditEvent(tenant, policy, result);
        }
        return result;
    }

    private void emitAuditEvent(TenantContext tenant, RetentionPolicy policy, PurgeResult result) {
        AuditEvent event = AuditEvent.builder()
                .id("retention-" + clock.millis() + "-" + tenant.getTenantId())
                .timestamp(clock.instant())
                .tenantId(tenant.getTenantId())
                .actor("system:retention")
                .action("data.retention_purge")
                .resource(policy.onExpiry().name())
                .outcome(AuditEvent.Outcome.SUCCESS)
                .details(java.util.Map.of(
                        "transcriptsRemoved", result.transcriptsRemoved(),
                        "auditEventsRemoved", result.auditEventsRemoved(),
                        "elapsedMs", result.elapsed().toMillis()))
                .build();
        for (AuditLogger logger : auditLoggers) {
            try {
                logger.log(event);
            } catch (RuntimeException e) {
                log.warn("Failed to log retention purge event: {}", e.getMessage());
            }
        }
    }

    /** Summary of a single retention pass. */
    public record PurgeResult(int transcriptsRemoved, int auditEventsRemoved, Duration elapsed) {
        public static PurgeResult empty() {
            return new PurgeResult(0, 0, Duration.ZERO);
        }
    }
}
