package io.jaiclaw.cronmanager;

import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.cron.CronJobExecutor;
import io.jaiclaw.cron.CronJobStore;
import io.jaiclaw.cron.CronService;
import io.jaiclaw.cronmanager.agent.CronAgentFactory;
import io.jaiclaw.cronmanager.batch.CronBatchJobFactory;
import io.jaiclaw.cronmanager.mcp.CronManagerMcpToolProvider;
import io.jaiclaw.cronmanager.model.CronJobDefinition;
import io.jaiclaw.cronmanager.persistence.CronExecutionStore;
import io.jaiclaw.cronmanager.persistence.CronJobDefinitionStore;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Auto-configuration for the Cron Manager extension.
 * Activates only when {@code jaiclaw.cron.manager.enabled=true}.
 * <p>
 * Does NOT provide {@link io.jaiclaw.gateway.mcp.McpServerRegistry} or
 * {@link io.jaiclaw.gateway.mcp.McpController} — those are provided by the gateway's
 * auto-configuration (embedded mode) or the app's standalone configuration.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "jaiclaw.cron.manager.enabled", havingValue = "true")
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
