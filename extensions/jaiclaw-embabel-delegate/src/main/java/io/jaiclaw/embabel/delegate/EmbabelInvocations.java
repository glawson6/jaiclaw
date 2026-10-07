package io.jaiclaw.embabel.delegate;

import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
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
        Agent matched = agentPlatform.agents().stream()
                .filter(a -> a.getName().equals(workflowName))
                .findFirst()
                .orElse(null);
        if (matched == null) {
            java.util.List<String> available = agentPlatform.agents().stream()
                    .map(Agent::getName).toList();
            log.info("Embabel agent lookup — workflow={} resolved=false available={}",
                    workflowName, available);
            throw new IllegalStateException(
                    "No Embabel agent named '" + workflowName + "' found. Available: " + available);
        }
        log.info("Embabel agent lookup — workflow={} resolved=true", workflowName);
        return matched;
    }

    /**
     * Run the given agent on the platform with the supplied input map
     * (default binding is {@code "it" -> userInput}). Returns the raw
     * {@link AgentProcess}; callers handle status interpretation.
     */
    /**
     * Runs the agent with the thread context class loader pinned to the one
     * that loaded this class.
     *
     * <p>Embabel's {@code JvmType} resolves every {@code @Action} parameter and
     * return type with {@code Class.forName(name, false,
     * Thread.currentThread().getContextClassLoader())}. Inside a Spring Boot
     * fat jar the application's classes live under {@code BOOT-INF/classes}
     * and are visible only to Boot's {@code LaunchedClassLoader}; a thread
     * whose context loader is the JDK application loader — every
     * {@code ForkJoinPool.commonPool} worker, and Camel's SEDA consumers —
     * therefore fails with {@code ClassNotFoundException} for the adopter's
     * own domain types on the first run. Unit tests never see this because
     * they run on a flat classpath where both loaders are the same object.
     *
     * <p>The loader that loaded this class is the right one: on a flat
     * classpath it is the app loader, in a fat jar it is the launched loader,
     * and in either case it is the loader the adopter's agent beans came from.
     */
    static AgentProcess run(AgentPlatform agentPlatform, Agent agent, Map<String, Object> input) {
        log.info("Embabel run — workflow={} input-keys={}", agent.getName(), input.keySet());
        Thread current = Thread.currentThread();
        ClassLoader previous = current.getContextClassLoader();
        ClassLoader pinned = contextClassLoaderFor(agent);
        current.setContextClassLoader(pinned);
        try {
            return agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, input);
        } finally {
            current.setContextClassLoader(previous);
        }
    }

    /**
     * The loader to run under: this module's own, which in every deployment
     * shape can see both Embabel and the application's classes. Falls back to
     * the system loader only if this class somehow has none (bootstrap).
     */
    static ClassLoader contextClassLoaderFor(Agent agent) {
        ClassLoader own = EmbabelInvocations.class.getClassLoader();
        return own != null ? own : ClassLoader.getSystemClassLoader();
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
            log.info("Embabel result extracted — blackboard last-result is null");
            return null;
        }
        String content;
        if (result instanceof String s) {
            content = s;
        } else {
            try {
                content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            } catch (JacksonException e) {
                log.warn("Failed to serialize Embabel result as JSON, falling back to toString()", e);
                content = result.toString();
            }
        }
        log.info("Embabel result extracted — type={} length={}",
                result.getClass().getSimpleName(), content.length());
        return content;
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
