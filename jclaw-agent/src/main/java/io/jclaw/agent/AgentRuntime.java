package io.jclaw.agent;

import io.jclaw.agent.session.SessionManager;
import io.jclaw.core.model.AssistantMessage;
import io.jclaw.core.model.UserMessage;
import io.jclaw.core.skill.SkillDefinition;
import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolContext;
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
 */
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final SessionManager sessionManager;
    private final ChatClient.Builder chatClientBuilder;
    private final SystemPromptBuilder systemPromptBuilder;
    private final List<org.springframework.ai.tool.ToolCallback> springToolCallbacks;
    private final Map<String, CompletableFuture<?>> activeTasks = new ConcurrentHashMap<>();

    public AgentRuntime(
            SessionManager sessionManager,
            ChatClient.Builder chatClientBuilder,
            List<ToolCallback> jclawTools,
            List<SkillDefinition> skills) {
        this.sessionManager = sessionManager;
        this.chatClientBuilder = chatClientBuilder;
        this.systemPromptBuilder = new SystemPromptBuilder()
                .tools(jclawTools)
                .skills(skills);

        // Bridge JClaw tools to Spring AI
        this.springToolCallbacks = jclawTools.stream()
                .map(t -> (org.springframework.ai.tool.ToolCallback)
                        new io.jclaw.tools.bridge.SpringAiToolBridge(t))
                .toList();
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
        var userMessage = new UserMessage(
                UUID.randomUUID().toString(), userInput, "user");
        sessionManager.appendMessage(context.sessionKey(), userMessage);

        // Build system prompt
        String systemPrompt = systemPromptBuilder
                .identity(context.identity())
                .build();

        // Build conversation history for context
        var session = sessionManager.get(context.sessionKey()).orElse(context.session());
        var historyMessages = session.messages().stream()
                .map(this::toSpringAiMessage)
                .toList();

        // Call LLM via Spring AI ChatClient with tools
        var chatClient = chatClientBuilder.build();
        var callSpec = chatClient.prompt()
                .system(systemPrompt)
                .user(userInput)
                .toolCallbacks(springToolCallbacks.toArray(new org.springframework.ai.tool.ToolCallback[0]));

        var chatResponse = callSpec.call();
        String responseContent = chatResponse.content();

        // Record assistant message
        var assistantMessage = new AssistantMessage(
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
        var task = activeTasks.get(sessionKey);
        if (task != null) {
            task.cancel(true);
            activeTasks.remove(sessionKey);
        }
    }

    public boolean isRunning(String sessionKey) {
        var task = activeTasks.get(sessionKey);
        return task != null && !task.isDone();
    }
}
