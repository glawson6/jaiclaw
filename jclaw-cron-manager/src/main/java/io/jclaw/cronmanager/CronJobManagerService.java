package io.jclaw.cronmanager;

import io.jclaw.core.model.CronJob;
import io.jclaw.cron.CronService;
import io.jclaw.cronmanager.batch.CronBatchJobFactory;
import io.jclaw.cronmanager.model.CronExecutionRecord;
import io.jclaw.cronmanager.model.CronJobDefinition;
import io.jclaw.cronmanager.persistence.CronExecutionStore;
import io.jclaw.cronmanager.persistence.CronJobDefinitionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Central orchestrator coordinating cron job management: CRUD operations,
 * scheduling via {@link CronService}, execution via Spring Batch, and
 * persistent history via {@link CronExecutionStore}.
 */
public class CronJobManagerService {

    private static final Logger log = LoggerFactory.getLogger(CronJobManagerService.class);

    private final CronJobDefinitionStore definitionStore;
    private final CronExecutionStore executionStore;
    private final CronService cronService;
    private final CronBatchJobFactory batchJobFactory;
    private final JobLauncher jobLauncher;

    public CronJobManagerService(CronJobDefinitionStore definitionStore,
                                 CronExecutionStore executionStore,
                                 CronService cronService,
                                 CronBatchJobFactory batchJobFactory,
                                 JobLauncher jobLauncher) {
        this.definitionStore = definitionStore;
        this.executionStore = executionStore;
        this.cronService = cronService;
        this.batchJobFactory = batchJobFactory;
        this.jobLauncher = jobLauncher;
    }

    /**
     * Initialize on startup: crash recovery + load enabled jobs into scheduler.
     */
    public void initialize() {
        // Crash recovery: mark orphaned STARTED executions as FAILED
        List<CronExecutionRecord> orphaned = executionStore.findStartedButNotCompleted();
        for (CronExecutionRecord record : orphaned) {
            log.warn("Marking orphaned execution as FAILED: runId={}, jobId={}",
                    record.runId(), record.jobId());
            executionStore.updateStatus(record.runId(), CronExecutionRecord.STATUS_FAILED,
                    "Process terminated during execution (crash recovery)", Instant.now());
        }
        if (!orphaned.isEmpty()) {
            log.info("Crash recovery: marked {} orphaned executions as FAILED", orphaned.size());
        }

        // Start the scheduler with enabled jobs
        cronService.start();
        log.info("Cron Job Manager initialized with {} jobs ({} enabled)",
                definitionStore.findAll().size(), definitionStore.findEnabled().size());
    }

    /**
     * Create a new cron job definition and schedule it if enabled.
     */
    public CronJobDefinition createJob(CronJobDefinition definition) {
        CronJob cronJob = definition.cronJob();
        // Assign ID if not set
        if (cronJob.id() == null || cronJob.id().isBlank()) {
            cronJob = new CronJob(
                    UUID.randomUUID().toString(), cronJob.name(), cronJob.agentId(),
                    cronJob.schedule(), cronJob.timezone(), cronJob.prompt(),
                    cronJob.deliveryChannel(), cronJob.deliveryTarget(),
                    cronJob.enabled(), cronJob.lastRunAt(), cronJob.nextRunAt());
            definition = definition.withCronJob(cronJob);
        }

        // Save to persistent store
        definitionStore.save(definition);

        // Schedule via CronService (which also computes nextRunAt)
        CronJob scheduled = cronService.addJob(cronJob);
        definition = definition.withCronJob(scheduled);
        definitionStore.save(definition);

        log.info("Created cron job: {} (id={}, schedule={})",
                cronJob.name(), cronJob.id(), cronJob.schedule());
        return definition;
    }

    /**
     * Get a job definition by ID.
     */
    public Optional<CronJobDefinition> getJob(String jobId) {
        return definitionStore.findById(jobId);
    }

    /**
     * List all job definitions.
     */
    public List<CronJobDefinition> listJobs() {
        return definitionStore.findAll();
    }

    /**
     * Delete a job by ID.
     */
    public boolean deleteJob(String jobId) {
        cronService.removeJob(jobId);
        boolean deleted = definitionStore.deleteById(jobId);
        if (deleted) {
            log.info("Deleted cron job: {}", jobId);
        }
        return deleted;
    }

    /**
     * Execute a job immediately via Spring Batch.
     */
    public String runNow(String jobId) {
        Optional<CronJobDefinition> optDef = definitionStore.findById(jobId);
        if (optDef.isEmpty()) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }

        CronJobDefinition jobDef = optDef.get();
        String runId = UUID.randomUUID().toString();

        try {
            Job batchJob = batchJobFactory.createJob(jobDef, runId);
            JobParameters params = new JobParametersBuilder()
                    .addString("runId", runId)
                    .addString("jobId", jobId)
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(batchJob, params);
            log.info("Launched immediate execution for job '{}' (runId={})",
                    jobDef.cronJob().name(), runId);
            return runId;
        } catch (Exception e) {
            log.error("Failed to launch immediate execution for job '{}'",
                    jobDef.cronJob().name(), e);
            throw new RuntimeException("Failed to launch job: " + e.getMessage(), e);
        }
    }

    /**
     * Get execution history for a specific job.
     */
    public List<CronExecutionRecord> getJobHistory(String jobId, int limit) {
        return executionStore.findByJobId(jobId, limit);
    }

    /**
     * Pause a job (disable scheduling).
     */
    public boolean pauseJob(String jobId) {
        Optional<CronJobDefinition> optDef = definitionStore.findById(jobId);
        if (optDef.isEmpty()) return false;

        definitionStore.updateEnabled(jobId, false);

        // Update the CronJob in CronService
        CronJobDefinition def = optDef.get();
        CronJob disabled = def.cronJob().withEnabled(false);
        cronService.removeJob(jobId);
        // Re-save with updated enabled state so CronService store stays in sync
        cronService.addJob(disabled);

        log.info("Paused cron job: {} (id={})", def.cronJob().name(), jobId);
        return true;
    }

    /**
     * Resume a paused job (re-enable scheduling).
     */
    public boolean resumeJob(String jobId) {
        Optional<CronJobDefinition> optDef = definitionStore.findById(jobId);
        if (optDef.isEmpty()) return false;

        definitionStore.updateEnabled(jobId, true);

        // Update and reschedule via CronService
        CronJobDefinition def = optDef.get();
        CronJob enabled = def.cronJob().withEnabled(true);
        cronService.removeJob(jobId);
        cronService.addJob(enabled);

        log.info("Resumed cron job: {} (id={})", def.cronJob().name(), jobId);
        return true;
    }

    /**
     * Gracefully shut down the scheduler.
     */
    public void shutdown() {
        cronService.stop();
        log.info("Cron Job Manager shut down");
    }
}
