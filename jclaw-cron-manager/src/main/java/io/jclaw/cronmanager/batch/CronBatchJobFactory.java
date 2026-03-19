package io.jclaw.cronmanager.batch;

import io.jclaw.cronmanager.agent.CronAgentFactory;
import io.jclaw.cronmanager.model.CronJobDefinition;
import io.jclaw.cronmanager.persistence.CronExecutionStore;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Factory for creating parameterized Spring Batch {@link Job} instances
 * that execute a specific cron job definition.
 */
public class CronBatchJobFactory {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CronAgentFactory agentFactory;
    private final CronExecutionStore executionStore;

    public CronBatchJobFactory(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               CronAgentFactory agentFactory,
                               CronExecutionStore executionStore) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.agentFactory = agentFactory;
        this.executionStore = executionStore;
    }

    /**
     * Create a Spring Batch Job for a specific cron job execution.
     *
     * @param jobDef the cron job definition to execute
     * @param runId  unique execution identifier
     * @return a configured Spring Batch Job
     */
    public Job createJob(CronJobDefinition jobDef, String runId) {
        String batchJobName = "cron-" + jobDef.cronJob().id() + "-" + runId;

        CronJobTasklet tasklet = new CronJobTasklet(jobDef, runId, agentFactory, executionStore);

        Step step = new StepBuilder("execute-" + runId, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();

        return new JobBuilder(batchJobName, jobRepository)
                .start(step)
                .build();
    }
}
