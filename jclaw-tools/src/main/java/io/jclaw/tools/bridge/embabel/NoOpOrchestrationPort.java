package io.jclaw.tools.bridge.embabel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * No-op implementation of AgentOrchestrationPort used when no
 * orchestration platform is configured.
 */
public class NoOpOrchestrationPort implements AgentOrchestrationPort {

    @Override
    public CompletableFuture<OrchestrationResult> execute(String workflowName, Map<String, Object> input) {
        return CompletableFuture.completedFuture(
                OrchestrationResult.failure("No orchestration platform configured"));
    }

    @Override
    public List<WorkflowDescriptor> listWorkflows() {
        return List.of();
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String platformName() {
        return "none";
    }
}
