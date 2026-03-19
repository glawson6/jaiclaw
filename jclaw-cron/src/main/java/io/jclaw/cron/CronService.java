package io.jclaw.cron;

import io.jclaw.core.model.CronJob;
import io.jclaw.core.model.CronJobResult;
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

    public CronService(CronJobStore jobStore, CronJobExecutor executor,
                       int maxConcurrentJobs, int jobTimeoutSeconds) {
        this.jobStore = jobStore;
        this.executor = executor;
        this.scheduleComputer = new CronScheduleComputer();
        this.maxConcurrentJobs = maxConcurrentJobs;
        this.jobTimeoutSeconds = jobTimeoutSeconds;
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
        Instant nextRun = scheduleComputer.nextFireTime(job.schedule(), job.timezone())
                .orElse(null);
        CronJob withNext = job.withNextRunAt(nextRun);
        jobStore.save(withNext);
        if (withNext.enabled()) scheduleJob(withNext);
        return withNext;
    }

    public boolean removeJob(String jobId) {
        cancelScheduled(jobId);
        return jobStore.remove(jobId);
    }

    public List<CronJob> listJobs() {
        return jobStore.listAll();
    }

    public Optional<CronJob> getJob(String jobId) {
        return jobStore.get(jobId);
    }

    public CronJobResult runNow(String jobId) {
        Optional<CronJob> job = jobStore.get(jobId);
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
        return history.stream()
                .filter(r -> jobId(r).equals(jobId))
                .toList();
    }

    public List<CronJobResult> getFullHistory() {
        return List.copyOf(history);
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
