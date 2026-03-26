package io.jclaw.cronmanager;

import io.jclaw.agent.AgentRuntime;
import io.jclaw.agent.session.SessionManager;
import io.jclaw.cron.CronJobExecutor;
import io.jclaw.cron.CronJobStore;
import io.jclaw.cron.CronService;
import io.jclaw.cronmanager.agent.CronAgentFactory;
import io.jclaw.cronmanager.batch.CronBatchJobFactory;
import io.jclaw.cronmanager.mcp.CronManagerMcpToolProvider;
import io.jclaw.cronmanager.model.CronJobDefinition;
import io.jclaw.cronmanager.persistence.CronExecutionStore;
import io.jclaw.cronmanager.persistence.CronJobDefinitionStore;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Auto-configuration for the Cron Manager extension.
 * Activates only when {@code jclaw.cron.manager.enabled=true}.
 * <p>
 * Does NOT provide {@link io.jclaw.gateway.mcp.McpServerRegistry} or
 * {@link io.jclaw.gateway.mcp.McpController} — those are provided by the gateway's
 * auto-configuration (embedded mode) or the app's standalone configuration.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "jclaw.cron.manager.enabled", havingValue = "true")
class CronManagerAutoConfiguration {

    @Bean
    CronAgentFactory cronAgentFactory(SessionManager sessionManager, AgentRuntime agentRuntime) {
        return new CronAgentFactory(sessionManager, agentRuntime);
    }

    @Bean
    CronJobExecutor cronJobExecutor(CronJobDefinitionStore definitionStore,
                                    CronAgentFactory agentFactory) {
        return new CronJobExecutor(job -> {
            CronJobDefinition def = definitionStore.findById(job.id())
                    .orElse(new CronJobDefinition(job));
            return agentFactory.executeJob(def);
        });
    }

    @Bean
    CronService cronService(CronJobStore cronJobStore, CronJobExecutor cronJobExecutor) {
        return new CronService(cronJobStore, cronJobExecutor, 5, 600);
    }

    @Bean
    CronBatchJobFactory cronBatchJobFactory(JobRepository jobRepository,
                                            PlatformTransactionManager transactionManager,
                                            CronAgentFactory agentFactory,
                                            CronExecutionStore executionStore) {
        return new CronBatchJobFactory(jobRepository, transactionManager, agentFactory, executionStore);
    }

    @Bean
    CronJobManagerService cronJobManagerService(CronJobDefinitionStore definitionStore,
                                                CronExecutionStore executionStore,
                                                CronService cronService,
                                                CronBatchJobFactory batchJobFactory,
                                                JobLauncher jobLauncher) {
        return new CronJobManagerService(definitionStore, executionStore, cronService,
                batchJobFactory, jobLauncher);
    }

    @Bean
    CronManagerMcpToolProvider cronManagerMcpToolProvider(CronJobManagerService managerService) {
        return new CronManagerMcpToolProvider(managerService);
    }

    @Bean
    CronManagerLifecycle cronManagerLifecycle(CronJobManagerService managerService) {
        return new CronManagerLifecycle(managerService);
    }
}
