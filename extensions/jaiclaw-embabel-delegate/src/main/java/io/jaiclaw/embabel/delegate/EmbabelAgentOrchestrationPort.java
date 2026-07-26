package io.jaiclaw.embabel.delegate;

import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort;
import io.jaiclaw.tools.bridge.embabel.OrchestrationResult;
import io.jaiclaw.tools.bridge.embabel.WorkflowDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Embabel-backed {@link AgentOrchestrationPort} for pipeline {@code AGENT}
 * stages that request {@code runtime: EMBABEL}.
 *
 * <p>Shares the lookup + execution + result-extraction logic with
 * {@link EmbabelAgentLoopDelegate} via {@link EmbabelInvocations}, so a
 * fix to one path applies to both.
 *
 * <p>{@code execute(...)} runs synchronously on the calling thread inside
 * a {@link CompletableFuture#supplyAsync} envelope so pipeline stages
 * can apply their own timeout to the returned future.
 */
public class EmbabelAgentOrchestrationPort implements AgentOrchestrationPort {

    private static final Logger log = LoggerFactory.getLogger(EmbabelAgentOrchestrationPort.class);

    private final AgentPlatform agentPlatform;
    private final ObjectMapper objectMapper;

    public EmbabelAgentOrchestrationPort(AgentPlatform agentPlatform, ObjectMapper objectMapper) {
        this.agentPlatform = agentPlatform;
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<OrchestrationResult> execute(String workflowName, Map<String, Object> input) {
        return CompletableFuture.supplyAsync(() -> {
            long startNanos = System.nanoTime();
            log.info("Embabel orchestration port — execute start workflow={} input-keys={} input-size~={}",
                    workflowName, input.keySet(),
                    input.values().stream()
                            .mapToInt(v -> v == null ? 0 : v.toString().length())
                            .sum());
            try {
                Agent agent = EmbabelInvocations.findAgent(agentPlatform, workflowName);
                AgentProcess process = EmbabelInvocations.run(agentPlatform, agent, input);
                if (EmbabelInvocations.completed(process)) {
                    String content = EmbabelInvocations.extractResult(process, objectMapper);
                    if (content == null) {
                        log.warn("Embabel agent '{}' completed but blackboard has no last result",
                                workflowName);
                        return OrchestrationResult.failure(
                                "Agent completed but produced no result");
                    }
                    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    log.info("Embabel orchestration port — execute end workflow={} success=true duration={}ms output-length={}",
                            workflowName, elapsedMs, content.length());
                    return OrchestrationResult.success(content);
                }
                String failureInfo = EmbabelInvocations.failureInfo(process);
                log.error("Embabel agent '{}' did not complete: {}", workflowName, failureInfo);
                return OrchestrationResult.failure("Agent execution failed: " + failureInfo);
            } catch (Exception e) {
                log.error("Error executing Embabel agent '{}'", workflowName, e);
                return OrchestrationResult.failure("Agent execution error: " + e.getMessage());
            }
        });
    }

    @Override
    public List<WorkflowDescriptor> listWorkflows() {
        return agentPlatform.agents().stream()
                .map(a -> WorkflowDescriptor.of(a.getName(), a.getDescription()))
                .toList();
    }

    @Override
    public boolean isAvailable() {
        return agentPlatform != null;
    }

    @Override
    public String platformName() {
        return "embabel";
    }
}
