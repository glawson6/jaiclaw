package io.jclaw.tools.bridge.embabel;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * SPI for delegating to an external agent orchestration platform (e.g., Embabel).
 * Implementations bridge JClaw's agent runtime to external orchestrators.
 */
public interface AgentOrchestrationPort {

    /**
     * Execute an orchestrated workflow by name.
     *
     * @param workflowName  the name of the workflow/agent to run
     * @param input         input data for the workflow
     * @return future with the workflow result
     */
    CompletableFuture<OrchestrationResult> execute(String workflowName, Map<String, Object> input);

    /**
     * List available workflows/agents from the orchestrator.
     */
    java.util.List<WorkflowDescriptor> listWorkflows();

    /**
     * Whether this port is connected and ready.
     */
    boolean isAvailable();

    /**
     * Name of the orchestration platform.
     */
    String platformName();
}
