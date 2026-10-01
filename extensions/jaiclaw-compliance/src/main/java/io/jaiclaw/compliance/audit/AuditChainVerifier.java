package io.jaiclaw.compliance.audit;

import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.core.api.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Periodically replays each tenant's audit hash chain and raises an alarm when
 * it does not verify.
 *
 * <p>{@link HashChainedAuditLogger#verifyChain(String)} existed with
 * <strong>zero callers</strong>: the chain was maintained on every write but
 * nobody ever checked it, so tampering would have been detected only if an
 * operator happened to run the check by hand. A tamper-evident log that nothing
 * reads is not evidence of anything.
 *
 * <h2>Why a virtual-thread loop and not {@code @Scheduled}</h2>
 *
 * <p>{@code @EnableScheduling} appears exactly once in this repository, in an
 * unrelated extension, so a {@code @Scheduled} method here would silently never
 * fire in most deployments. {@code CuratorScheduler} records the same decision
 * for framework-internal work: a fixed-delay loop on a daemon virtual thread
 * also works in a plain-Java embedding, not only under Spring.
 *
 * <p>The first pass is deferred by one interval. Verification reads an entire
 * tenant history, which is not work to add to application startup.
 *
 * <h2>Alerting</h2>
 *
 * <p>There is no notification SPI in the framework, so a break is reported three
 * ways and the operator wires whichever they already consume:
 *
 * <ol>
 *   <li>an {@code audit.integrity_violation} event, already emitted by
 *       {@code verifyChain} — the SIEM-rule path, and the one to prefer;</li>
 *   <li>{@code log.error}, for log-based alerting;</li>
 *   <li>a counter, readable from {@link #failureCount()} and surfaced by
 *       whatever metrics binding the deployment has.</li>
 * </ol>
 *
 * <p><strong>Truncation is still not detectable.</strong> {@code verifyChain}
 * replays the records that are present and has no persisted chain head, so a
 * deleted tail leaves a chain that verifies clean. Scheduling the check does not
 * change that. Ship audit records to append-only storage and alert on volume
 * gaps — see {@code docs/user/COMPLIANCE-HOWTO.md}.
 */
@Experimental
public class AuditChainVerifier implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AuditChainVerifier.class);

    /** Daily. The chain only changes as fast as audit events are written. */
    public static final Duration DEFAULT_INTERVAL = Duration.ofHours(24);

    /** Directory name {@code FileAuditLogger} uses for the single-tenant case. */
    private static final String DEFAULT_TENANT_DIR = "_default";

    private final Collection<AuditLogger> auditLoggers;
    private final Path auditStoreDir;
    private final Duration interval;
    private final Clock clock;

    private volatile Thread worker;
    private volatile boolean running;
    private volatile Instant lastRunAt;
    private volatile int lastTenantsChecked;
    private volatile int failureCount;

    public AuditChainVerifier(Collection<AuditLogger> auditLoggers, Path auditStoreDir) {
        this(auditLoggers, auditStoreDir, DEFAULT_INTERVAL, Clock.systemUTC());
    }

    public AuditChainVerifier(Collection<AuditLogger> auditLoggers, Path auditStoreDir,
                              Duration interval, Clock clock) {
        this.auditLoggers = auditLoggers == null ? List.of() : List.copyOf(auditLoggers);
        this.auditStoreDir = auditStoreDir;
        this.interval = interval == null || interval.isZero() || interval.isNegative()
                ? DEFAULT_INTERVAL : interval;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** Starts the background sweep. No-op when no chained logger is present. */
    public synchronized void start() {
        if (running) return;
        if (chainedLoggers().isEmpty()) {
            log.debug("No HashChainedAuditLogger present; chain verification not scheduled");
            return;
        }
        running = true;
        worker = Thread.ofVirtual().name("audit-chain-verifier").start(this::loop);
        log.info("Audit chain verification scheduled every {} (first pass deferred by one interval)",
                interval);
    }

    @Override
    public synchronized void close() {
        running = false;
        Thread t = worker;
        if (t != null) t.interrupt();
        worker = null;
    }

    private void loop() {
        while (running) {
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!running) return;
            try {
                verifyOnce();
            } catch (RuntimeException e) {
                // A sweep that throws must not kill the scheduler — the next
                // interval should still run.
                log.error("Audit chain verification pass failed", e);
            }
        }
    }

    /**
     * Runs one verification pass across every discovered tenant.
     *
     * <p>Package-visible so tests drive it directly rather than waiting on the
     * interval.
     *
     * @return the chains that failed; empty when everything verified
     */
    public List<HashChainedAuditLogger.IntegrityReport> verifyOnce() {
        List<HashChainedAuditLogger> chained = chainedLoggers();
        if (chained.isEmpty()) {
            return List.of();
        }
        Set<String> tenants = discoverTenants();
        List<HashChainedAuditLogger.IntegrityReport> failures = new ArrayList<>();

        for (HashChainedAuditLogger logger : chained) {
            for (String tenantId : tenants) {
                HashChainedAuditLogger.IntegrityReport report = logger.verifyChain(tenantId);
                if (!report.valid()) {
                    failureCount++;
                    failures.add(report);
                    // verifyChain already wrote audit.integrity_violation for the
                    // SIEM; this is the log-alerting half.
                    log.error("AUDIT CHAIN BROKEN for tenant '{}' at index {} (event {}): {}",
                            tenantId, report.brokenAt(), report.offendingEventId(), report.reason());
                }
            }
        }
        lastRunAt = clock.instant();
        lastTenantsChecked = tenants.size();
        if (failures.isEmpty()) {
            log.info("Audit chain verification passed for {} tenant(s)", tenants.size());
        }
        return failures;
    }

    /**
     * Tenants to check, discovered from the audit store directory layout
     * ({@code {storeDir}/{tenantId}/}).
     *
     * <p>Discovery is from disk rather than a registry because <strong>no tenant
     * registry exists</strong> — {@code TenantGuard} resolves only the current
     * thread's tenant and throws in MULTI mode on a thread with no context, which
     * a scheduler thread is. {@code CuratorScheduler} discovers from disk for the
     * same reason, with the same upside: a tenant whose data exists but which is
     * no longer configured still gets checked, instead of being quietly skipped.
     */
    Set<String> discoverTenants() {
        Set<String> tenants = new LinkedHashSet<>();
        if (auditStoreDir != null && Files.isDirectory(auditStoreDir)) {
            try (Stream<Path> dirs = Files.list(auditStoreDir)) {
                dirs.filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .forEach(tenants::add);
            } catch (IOException e) {
                log.warn("Could not enumerate audit store at {} — falling back to the default tenant",
                        auditStoreDir, e);
            }
        }
        if (tenants.isEmpty()) {
            // Single-tenant deployments, and any case where the store has not
            // been written yet. Checking nothing would make a green result
            // meaningless.
            tenants.add(DEFAULT_TENANT_DIR);
        }
        return tenants;
    }

    private List<HashChainedAuditLogger> chainedLoggers() {
        List<HashChainedAuditLogger> out = new ArrayList<>();
        for (AuditLogger logger : auditLoggers) {
            // The BPP wraps outermost, so the bean IS the decorator when enabled.
            if (logger instanceof HashChainedAuditLogger chained) {
                out.add(chained);
            }
        }
        return out;
    }

    /** When the last pass completed, or null if none has. */
    public Instant lastRunAt() {
        return lastRunAt;
    }

    /** Tenants examined in the last pass. */
    public int lastTenantsChecked() {
        return lastTenantsChecked;
    }

    /** Cumulative count of broken chains seen — drive a metric or an alert from this. */
    public int failureCount() {
        return failureCount;
    }

    /** True when the sweep thread is live. */
    public boolean isRunning() {
        return running;
    }
}
