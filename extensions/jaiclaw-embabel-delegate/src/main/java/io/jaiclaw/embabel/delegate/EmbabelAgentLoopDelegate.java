package io.jaiclaw.embabel.delegate;

import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.agent.delegate.AgentLoopDelegate;
import io.jaiclaw.agent.delegate.AgentLoopDelegateContext;
import io.jaiclaw.agent.delegate.AgentLoopDelegateResult;
import io.jaiclaw.config.TenantAgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Bridges JaiClaw's message pipeline to Embabel's GOAP agent runtime.
 *
 * <p>When a tenant's agent config specifies {@code loop-delegate.delegate-id: embabel},
 * this delegate takes over from the default LLM+tool loop. The {@code workflow} field
 * maps to an Embabel {@link Agent} by name. User input is bound to the blackboard
 * via {@code IoBinding.DEFAULT_BINDING} ("it"), and the GOAP planner chains actions
 * until the goal type is achieved. The goal object is serialized to JSON and returned
 * as the assistant response.
 *
 * <p>Shares lookup + execution + result-extraction logic with
 * {@link EmbabelAgentOrchestrationPort} via {@link EmbabelInvocations}.
 */
public class EmbabelAgentLoopDelegate implements AgentLoopDelegate {

    private static final Logger log = LoggerFactory.getLogger(EmbabelAgentLoopDelegate.class);

    public static final String DELEGATE_ID = "embabel";

    private final AgentPlatform agentPlatform;
    private final ObjectMapper objectMapper;

    public EmbabelAgentLoopDelegate(AgentPlatform agentPlatform, ObjectMapper objectMapper) {
        this.agentPlatform = agentPlatform;
        this.objectMapper = objectMapper;
    }

    @Override
    public String delegateId() {
        return DELEGATE_ID;
    }

    @Override
    public boolean canHandle(TenantAgentConfig config) {
        return config.loopDelegate() != null
                && config.loopDelegate().enabled()
                && DELEGATE_ID.equals(config.loopDelegate().delegateId());
    }

    @Override
    public AgentLoopDelegateResult execute(String userInput, AgentLoopDelegateContext context) {
        String workflowName = context.tenantConfig().loopDelegate().workflow();
        log.info("Executing Embabel agent '{}' with input length={}", workflowName, userInput.length());

        Agent agent = EmbabelInvocations.findAgent(agentPlatform, workflowName);

        try {
            AgentProcess process = EmbabelInvocations.run(
                    agentPlatform, agent, Map.of("it", userInput));

            if (EmbabelInvocations.completed(process)) {
                String content = EmbabelInvocations.extractResult(process, objectMapper);
                if (content == null) {
                    log.warn("Embabel agent '{}' completed but blackboard has no last result", workflowName);
                    return AgentLoopDelegateResult.failure(
                            "Agent completed but produced no result");
                }
                log.info("Embabel agent '{}' completed successfully", workflowName);
                return AgentLoopDelegateResult.success(content);
            } else {
                String failureInfo = EmbabelInvocations.failureInfo(process);
                log.error("Embabel agent '{}' did not complete: {}", workflowName, failureInfo);
                return AgentLoopDelegateResult.failure(
                        "Agent execution failed: " + failureInfo);
            }
        } catch (Exception e) {
            log.error("Error executing Embabel agent '{}'", workflowName, e);
            return AgentLoopDelegateResult.failure(
                    "Agent execution error: " + e.getMessage());
        }
    }
}
