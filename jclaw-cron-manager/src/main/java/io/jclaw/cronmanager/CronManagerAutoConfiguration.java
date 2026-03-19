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
import io.jclaw.gateway.mcp.McpController;
import io.jclaw.gateway.mcp.McpServerRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Auto-configuration for the Cron Manager beans.
 * Wires the agent factory, scheduler, batch integration, MCP, and orchestrator.
 */
@Configuration
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
    McpServerRegistry mcpServerRegistry(CronManagerMcpToolProvider mcpToolProvider) {
        McpServerRegistry registry = new McpServerRegistry();
        registry.register(mcpToolProvider);
        return registry;
    }

    @Bean
    McpController mcpController(McpServerRegistry registry) {
        return new McpController(registry);
    }
}
