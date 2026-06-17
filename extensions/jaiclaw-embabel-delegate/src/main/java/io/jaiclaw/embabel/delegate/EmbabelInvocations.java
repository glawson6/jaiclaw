package io.jaiclaw.embabel.delegate;

import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Shared helpers for invoking an Embabel {@link Agent} by name and
 * marshalling its blackboard result back into a JSON / String payload.
 *
 * <p>Both {@link EmbabelAgentLoopDelegate} (per-message agent loop) and
 * {@code EmbabelAgentOrchestrationPort} (per-pipeline-stage execution
 * via {@code AgentOrchestrationPort}) share this code so any fix to
 * lookup, process status handling, or result serialization lands in
 * both call sites.
 *
 * <p>Package-private — implementation detail of the embabel-delegate
 * module.
 */
final class EmbabelInvocations {

    private static final Logger log = LoggerFactory.getLogger(EmbabelInvocations.class);

    private EmbabelInvocations() {}

    /**
     * Look up an Embabel agent by exact name. Throws
     * {@link IllegalStateException} if no agent is registered under that
     * name, with the available names enumerated in the message.
     */
    static Agent findAgent(AgentPlatform agentPlatform, String workflowName) {
        return agentPlatform.agents().stream()
                .filter(a -> a.getName().equals(workflowName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No Embabel agent named '" + workflowName + "' found. Available: "
                                + agentPlatform.agents().stream().map(Agent::getName).toList()));
    }

    /**
     * Run the given agent on the platform with the supplied input map
     * (default binding is {@code "it" -> userInput}). Returns the raw
     * {@link AgentProcess}; callers handle status interpretation.
     */
    static AgentProcess run(AgentPlatform agentPlatform, Agent agent, Map<String, Object> input) {
        return agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, input);
    }

    /**
     * {@code true} iff the process terminated in
     * {@link AgentProcessStatusCode#COMPLETED}.
     */
    static boolean completed(AgentProcess process) {
        return process.getStatus() == AgentProcessStatusCode.COMPLETED;
    }

    /**
     * Extract the last blackboard result from a completed process and
     * serialize it to a String. Strings pass through; everything else
     * is rendered as pretty-printed JSON. Returns {@code null} only if
     * the blackboard itself has no last result — callers should treat
     * that as a soft failure.
     */
    static String extractResult(AgentProcess process, ObjectMapper objectMapper) {
        Object result = process.getBlackboard().lastResult();
        if (result == null) {
            return null;
        }
        if (result instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize Embabel result as JSON, falling back to toString()", e);
            return result.toString();
        }
    }

    /**
     * Render the failure-info field for diagnostic logging.
     */
    static String failureInfo(AgentProcess process) {
        return process.getFailureInfo() != null
                ? process.getFailureInfo().toString()
                : "status=" + process.getStatus();
    }
}
