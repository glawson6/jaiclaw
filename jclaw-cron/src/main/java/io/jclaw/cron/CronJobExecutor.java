package io.jclaw.cron;

import io.jclaw.core.model.CronJob;
import io.jclaw.core.model.CronJobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

/**
 * Executes a cron job by running an isolated agent session with the job's prompt.
 * The actual agent invocation is abstracted via a {@code Function<String, String>}
 * so this class has no dependency on AgentRuntime.
 */
public class CronJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(CronJobExecutor.class);

    private final Function<CronJob, String> agentRunner;

    /**
     * @param agentRunner function that takes a CronJob and returns the agent's response text
     */
    public CronJobExecutor(Function<CronJob, String> agentRunner) {
        this.agentRunner = agentRunner;
    }

    public CronJobResult execute(CronJob job) {
        String runId = UUID.randomUUID().toString();
        log.info("Executing cron job '{}' (id={}, runId={})", job.name(), job.id(), runId);

        try {
            String response = agentRunner.apply(job);
            log.info("Cron job '{}' completed successfully", job.name());
            return new CronJobResult.Success(job.id(), runId, response, Instant.now());
        } catch (Exception e) {
            log.error("Cron job '{}' failed: {}", job.name(), e.getMessage(), e);
            return new CronJobResult.Failure(job.id(), runId, e.getMessage(), Instant.now());
        }
    }
}
