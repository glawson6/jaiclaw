package io.jaiclaw.cronmanager.agent;

import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.AgentRuntimeContext;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.core.model.AgentIdentity;
import io.jaiclaw.core.model.AssistantMessage;
import io.jaiclaw.core.model.Session;
import io.jaiclaw.core.tenant.DefaultTenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.cronmanager.model.CronJobDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Creates per-execution agent sessions and runs the job's prompt through the agent runtime.
 * Each execution gets a fresh session key: {@code cron:{jobId}:{runId}}.
 */
public class CronAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(CronAgentFactory.class);

    private final SessionManager sessionManager;
    private final AgentRuntime agentRuntime;

    public CronAgentFactory(SessionManager sessionManager, AgentRuntime agentRuntime) {
        this.sessionManager = sessionManager;
        this.agentRuntime = agentRuntime;
    }

    /**
     * Execute a cron job definition through the agent runtime.
     *
     * @param jobDef the job definition to execute
     * @param runId  unique execution identifier
     * @return the agent's response text
     */
    public String executeJob(CronJobDefinition jobDef, String runId) {
        String jobId = jobDef.cronJob().id();
        String agentId = jobDef.cronJob().agentId();
        String tenantId = jobDef.cronJob().tenantId();
        String sessionKey = "cron:" + jobId + ":" + runId;
        String prompt = jobDef.cronJob().prompt();
        ToolProfile toolProfile = jobDef.toolProfile();

        log.info("Executing cron job '{}' (id={}, runId={}) with profile {}",
                jobDef.cronJob().name(), jobId, runId, toolProfile);

        // Set tenant context from job's tenantId if present
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContextHolder.set(new DefaultTenantContext(tenantId, tenantId));
        }

        try {
            Session session = sessionManager.getOrCreate(sessionKey, agentId);

            AgentRuntimeContext context = new AgentRuntimeContext(
                    agentId, sessionKey, session,
                    AgentIdentity.DEFAULT, toolProfile, ".");

            AssistantMessage response = agentRuntime.run(prompt, context).join();
            log.info("Cron job '{}' completed successfully", jobDef.cronJob().name());
            return response.content();
        } finally {
            // Clean up the ephemeral session
            sessionManager.reset(sessionKey);
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContextHolder.clear();
            }
        }
    }

    /**
     * Execute a cron job with an auto-generated run ID.
     */
    public String executeJob(CronJobDefinition jobDef) {
        return executeJob(jobDef, UUID.randomUUID().toString());
    }
}
