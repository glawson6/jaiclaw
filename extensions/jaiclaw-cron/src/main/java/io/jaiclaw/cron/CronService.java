package io.jaiclaw.cron;

import io.jaiclaw.core.model.CronJob;
import io.jaiclaw.core.model.CronJobResult;
import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages cron job scheduling: start/stop/pause jobs, compute next run times.
 * Uses a single-threaded {@link ScheduledExecutorService} for timer management.
 */
public class CronService {

    private static final Logger log = LoggerFactory.getLogger(CronService.class);

    private final CronJobStore jobStore;
    private final CronJobExecutor executor;
    private final CronScheduleComputer scheduleComputer;
    private final int maxConcurrentJobs;
    private final int jobTimeoutSeconds;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();
    private final List<CronJobResult> history = new CopyOnWriteArrayList<>();
    private final TenantGuard tenantGuard;

    public CronService(CronJobStore jobStore, CronJobExecutor executor,
                       int maxConcurrentJobs, int jobTimeoutSeconds) {
        this(jobStore, executor, maxConcurrentJobs, jobTimeoutSeconds, null);
    }

    public CronService(CronJobStore jobStore, CronJobExecutor executor,
                       int maxConcurrentJobs, int jobTimeoutSeconds,
                       TenantGuard tenantGuard) {
        this.jobStore = jobStore;
        this.executor = executor;
        this.scheduleComputer = new CronScheduleComputer();
        this.maxConcurrentJobs = maxConcurrentJobs;
        this.jobTimeoutSeconds = jobTimeoutSeconds;
        this.tenantGuard = tenantGuard;
        this.scheduler = Executors.newScheduledThreadPool(maxConcurrentJobs,
                Thread.ofVirtual().name("cron-", 0).factory());
    }

    public void start() {
        log.info("Starting cron service with {} jobs", jobStore.size());
        for (CronJob job : jobStore.listEnabled()) {
            scheduleJob(job);
        }
    }

    public void stop() {
        scheduledFutures.values().forEach(f -> f.cancel(false));
        scheduledFutures.clear();
        scheduler.shutdown();
        log.info("Cron service stopped");
    }

    public CronJob addJob(CronJob job) {
        // Stamp tenantId in MULTI mode
        if (tenantGuard != null && tenantGuard.isMultiTenant() && job.tenantId() == null) {
            job = job.withTenantId(tenantGuard.resolveTenantIdForStorage());
        }
        Instant nextRun = scheduleComputer.nextFireTime(job.schedule(), job.timezone())
                .orElse(null);
        CronJob withNext = job.withNextRunAt(nextRun);
        jobStore.save(withNext);
        if (withNext.enabled()) scheduleJob(withNext);
        return withNext;
    }

    public boolean removeJob(String jobId) {
        verifyTenantAccess(jobId);
        cancelScheduled(jobId);
        return jobStore.remove(jobId);
    }

    public List<CronJob> listJobs() {
        List<CronJob> all = jobStore.listAll();
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            return all.stream()
                    .filter(j -> tenantId.equals(j.tenantId()))
                    .toList();
        }
        return all;
    }

    public Optional<CronJob> getJob(String jobId) {
        Optional<CronJob> job = jobStore.get(jobId);
        if (tenantGuard != null && tenantGuard.isMultiTenant() && job.isPresent()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (!tenantId.equals(job.get().tenantId())) {
                return Optional.empty();
            }
        }
        return job;
    }

    public CronJobResult runNow(String jobId) {
        Optional<CronJob> job = getJob(jobId);
        if (job.isEmpty()) {
            return new CronJobResult.Failure(jobId, UUID.randomUUID().toString(),
                    "Job not found", Instant.now());
        }
        CronJobResult result = executor.execute(job.get());
        history.add(result);
        jobStore.save(job.get().withLastRunAt(Instant.now()));
        return result;
    }

    public List<CronJobResult> getHistory(String jobId) {
        // In MULTI mode, verify the job belongs to the current tenant before returning history
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            Optional<CronJob> job = getJob(jobId);
            if (job.isEmpty()) return List.of();
        }
        return history.stream()
                .filter(r -> jobId(r).equals(jobId))
                .toList();
    }

    public List<CronJobResult> getFullHistory() {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            // Filter history to only include results for jobs owned by the current tenant
            Set<String> tenantJobIds = listJobs().stream()
                    .map(CronJob::id)
                    .collect(java.util.stream.Collectors.toSet());
            return history.stream()
                    .filter(r -> tenantJobIds.contains(jobId(r)))
                    .toList();
        }
        return List.copyOf(history);
    }

    private void verifyTenantAccess(String jobId) {
        if (tenantGuard == null || !tenantGuard.isMultiTenant()) return;
        Optional<CronJob> job = jobStore.get(jobId);
        if (job.isPresent()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (!tenantId.equals(job.get().tenantId())) {
                throw new IllegalStateException(
                        "Cross-tenant access denied for cron job " + jobId);
            }
        }
    }

    private void scheduleJob(CronJob job) {
        cancelScheduled(job.id());
        scheduleComputer.nextFireTime(job.schedule(), job.timezone()).ifPresent(nextRun -> {
            long delayMs = Math.max(0, nextRun.toEpochMilli() - System.currentTimeMillis());
            ScheduledFuture<?> future = scheduler.schedule(() -> executeAndReschedule(job),
                    delayMs, TimeUnit.MILLISECONDS);
            scheduledFutures.put(job.id(), future);
            log.debug("Scheduled job '{}' to fire in {}ms", job.name(), delayMs);
        });
    }

    private void executeAndReschedule(CronJob job) {
        CronJobResult result = executor.execute(job);
        history.add(result);
        CronJob updated = job.withLastRunAt(Instant.now());
        Instant nextRun = scheduleComputer.nextFireTime(job.schedule(), job.timezone()).orElse(null);
        updated = updated.withNextRunAt(nextRun);
        jobStore.save(updated);
        if (updated.enabled()) scheduleJob(updated);
    }

    private void cancelScheduled(String jobId) {
        ScheduledFuture<?> existing = scheduledFutures.remove(jobId);
        if (existing != null) existing.cancel(false);
    }

    private String jobId(CronJobResult result) {
        return switch (result) {
            case CronJobResult.Success s -> s.jobId();
            case CronJobResult.Failure f -> f.jobId();
        };
    }
}
