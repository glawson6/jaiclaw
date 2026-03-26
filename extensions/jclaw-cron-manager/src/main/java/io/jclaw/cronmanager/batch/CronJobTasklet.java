package io.jclaw.cronmanager.batch;

import io.jclaw.cronmanager.agent.CronAgentFactory;
import io.jclaw.cronmanager.model.CronExecutionRecord;
import io.jclaw.cronmanager.model.CronJobDefinition;
import io.jclaw.cronmanager.persistence.CronExecutionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.Instant;

/**
 * Spring Batch {@link Tasklet} wrapping a single cron job execution.
 * Records STARTED, COMPLETED, or FAILED status in the execution store.
 */
public class CronJobTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(CronJobTasklet.class);

    private final CronJobDefinition jobDef;
    private final String runId;
    private final CronAgentFactory agentFactory;
    private final CronExecutionStore executionStore;

    public CronJobTasklet(CronJobDefinition jobDef, String runId,
                          CronAgentFactory agentFactory, CronExecutionStore executionStore) {
        this.jobDef = jobDef;
        this.runId = runId;
        this.agentFactory = agentFactory;
        this.executionStore = executionStore;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String jobId = jobDef.cronJob().id();
        String jobName = jobDef.cronJob().name();

        // Record execution start
        CronExecutionRecord startRecord = CronExecutionRecord.started(runId, jobId, jobName);
        executionStore.insert(startRecord);

        try {
            String result = agentFactory.executeJob(jobDef, runId);
            executionStore.updateStatus(runId, CronExecutionRecord.STATUS_COMPLETED,
                    result, Instant.now());
            log.info("Cron batch job completed: {} (runId={})", jobName, runId);
        } catch (Exception e) {
            executionStore.updateStatus(runId, CronExecutionRecord.STATUS_FAILED,
                    e.getMessage(), Instant.now());
            log.error("Cron batch job failed: {} (runId={})", jobName, runId, e);
            throw e;
        }

        return RepeatStatus.FINISHED;
    }
}
