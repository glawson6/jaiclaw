package io.jaiclaw.cli.github;

import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.AgentRuntimeContext;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.core.model.AssistantMessage;
import io.jaiclaw.core.model.Session;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Shared helper that all slash-command handlers use to invoke the LLM.
 *
 * <p>Mirrors the pattern in
 * {@code io.jaiclaw.shell.commands.ChatCommands#chat(String)} — resolve
 * the default agent id from properties, get-or-create a session, build
 * a context, invoke {@link AgentRuntime#run(String, AgentRuntimeContext)}.
 *
 * <p>The two adjustments this makes vs. the shell version:
 * <ul>
 *   <li>Session key is passed in explicitly (dispatcher derives it from
 *       {@code github:{owner}/{repo}#{issue}} so each thread has its
 *       own transient session).</li>
 *   <li>The caller can prepend a system prompt to the user text so the
 *       LLM has consistent guidance across every invocation.</li>
 * </ul>
 */
@Component
public class AgentInvoker {

    private final ObjectProvider<AgentRuntime> agentRuntimeProvider;
    private final SessionManager sessionManager;
    private final JaiClawProperties properties;

    public AgentInvoker(ObjectProvider<AgentRuntime> agentRuntimeProvider,
                        SessionManager sessionManager,
                        JaiClawProperties properties) {
        this.agentRuntimeProvider = agentRuntimeProvider;
        this.sessionManager = sessionManager;
        this.properties = properties;
    }

    /**
     * Invoke the agent and return the assistant's reply.
     *
     * @throws IllegalStateException if no {@link AgentRuntime} bean is
     *         wired — happens when no LLM provider was configured.
     */
    public String invoke(String sessionKey, String userText, String systemPromptPrefix) {
        AgentRuntime runtime = agentRuntimeProvider.getIfAvailable();
        if (runtime == null) {
            throw new IllegalStateException(
                    "No LLM configured — set ANTHROPIC_API_KEY, OPENAI_API_KEY, or another provider.");
        }
        String agentId = properties.agent().defaultAgent();
        Session session = sessionManager.getOrCreate(sessionKey, agentId);
        AgentRuntimeContext context = new AgentRuntimeContext(agentId, sessionKey, session);

        String prompt = (systemPromptPrefix == null || systemPromptPrefix.isBlank())
                ? userText
                : systemPromptPrefix + "\n\n---\n\nUser message:\n" + userText;

        AssistantMessage response = runtime.run(prompt, context).join();
        return response.content();
    }
}
