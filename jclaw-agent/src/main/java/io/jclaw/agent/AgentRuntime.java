package io.jclaw.agent;

import io.jclaw.agent.session.SessionManager;
import io.jclaw.core.model.AssistantMessage;
import io.jclaw.core.model.UserMessage;
import io.jclaw.core.skill.SkillDefinition;
import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent runtime — orchestrates the execution lifecycle from user input to assistant response.
 * Uses Spring AI ChatClient for LLM interaction with tool calling support.
 * Embabel's AgentPlatform can be layered on top for GOAP planning in future iterations.
 *
 * <p>Tools are resolved lazily from the {@link ToolRegistry} on each {@code run()} call,
 * allowing modules (e.g. jclaw-tools-k8s) to register tools after construction.
 */
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final SessionManager sessionManager;
    private final ChatClient.Builder chatClientBuilder;
    private final ToolRegistry toolRegistry;
    private final List<SkillDefinition> skills;
    private final Map<String, CompletableFuture<?>> activeTasks = new ConcurrentHashMap<>();

    public AgentRuntime(
            SessionManager sessionManager,
            ChatClient.Builder chatClientBuilder,
            ToolRegistry toolRegistry,
            List<SkillDefinition> skills) {
        this.sessionManager = sessionManager;
        this.chatClientBuilder = chatClientBuilder;
        this.toolRegistry = toolRegistry;
        this.skills = skills;
    }

    /**
     * Run the agent with the given user input and return an assistant response.
     */
    public CompletableFuture<AssistantMessage> run(String userInput, AgentRuntimeContext context) {
        var future = CompletableFuture.supplyAsync(() -> {
            try {
                return executeSync(userInput, context);
            } catch (Exception e) {
                log.error("Agent execution failed for session {}", context.sessionKey(), e);
                return new AssistantMessage(
                        UUID.randomUUID().toString(),
                        "I encountered an error: " + e.getMessage(),
                        "error"
                );
            }
        });

        activeTasks.put(context.sessionKey(), future);
        future.whenComplete((r, e) -> activeTasks.remove(context.sessionKey()));
        return future;
    }

    private AssistantMessage executeSync(String userInput, AgentRuntimeContext context) {
        // Record user message
        UserMessage userMessage = new UserMessage(
                UUID.randomUUID().toString(), userInput, "user");
        sessionManager.appendMessage(context.sessionKey(), userMessage);

        // Resolve tools lazily from registry
        List<ToolCallback> jclawTools = toolRegistry.resolveAll();

        // Build system prompt with current tools
        SystemPromptBuilder systemPromptBuilder = new SystemPromptBuilder()
                .tools(jclawTools)
                .skills(skills);

        String systemPrompt = systemPromptBuilder
                .identity(context.identity())
                .build();

        // Bridge JClaw tools to Spring AI
        List<org.springframework.ai.tool.ToolCallback> springToolCallbacks = jclawTools.stream()
                .map(t -> (org.springframework.ai.tool.ToolCallback)
                        new io.jclaw.tools.bridge.SpringAiToolBridge(t))
                .toList();

        // Build conversation history for context
        var session = sessionManager.get(context.sessionKey()).orElse(context.session());
        var historyMessages = session.messages().stream()
                .map(this::toSpringAiMessage)
                .toList();

        // Call LLM via Spring AI ChatClient with tools
        ChatClient chatClient = chatClientBuilder.build();
        var callSpec = chatClient.prompt()
                .system(systemPrompt)
                .user(userInput)
                .toolCallbacks(springToolCallbacks.toArray(new org.springframework.ai.tool.ToolCallback[0]));

        var chatResponse = callSpec.call();
        String responseContent = chatResponse.content();

        // Record assistant message
        AssistantMessage assistantMessage = new AssistantMessage(
                UUID.randomUUID().toString(),
                responseContent != null ? responseContent : "",
                "default"
        );
        sessionManager.appendMessage(context.sessionKey(), assistantMessage);

        return assistantMessage;
    }

    private org.springframework.ai.chat.messages.Message toSpringAiMessage(io.jclaw.core.model.Message msg) {
        return switch (msg) {
            case io.jclaw.core.model.UserMessage u ->
                    new org.springframework.ai.chat.messages.UserMessage(u.content());
            case io.jclaw.core.model.AssistantMessage a ->
                    new org.springframework.ai.chat.messages.AssistantMessage(a.content());
            case io.jclaw.core.model.SystemMessage s ->
                    new org.springframework.ai.chat.messages.SystemMessage(s.content());
            case io.jclaw.core.model.ToolResultMessage t ->
                    new org.springframework.ai.chat.messages.UserMessage("[Tool: " + t.toolName() + "] " + t.content());
        };
    }

    public void cancel(String sessionKey) {
        CompletableFuture<?> task = activeTasks.get(sessionKey);
        if (task != null) {
            task.cancel(true);
            activeTasks.remove(sessionKey);
        }
    }

    public boolean isRunning(String sessionKey) {
        CompletableFuture<?> task = activeTasks.get(sessionKey);
        return task != null && !task.isDone();
    }
}
