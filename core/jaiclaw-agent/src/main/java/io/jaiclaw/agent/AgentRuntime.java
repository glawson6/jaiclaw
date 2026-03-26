package io.jaiclaw.agent;

import io.jaiclaw.agent.loop.ExplicitToolLoop;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.core.agent.*;
import io.jaiclaw.core.hook.HookName;
import io.jaiclaw.core.model.AssistantMessage;
import io.jaiclaw.core.model.UserMessage;
import io.jaiclaw.core.skill.SkillDefinition;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.tools.ToolRegistry;
import io.jaiclaw.tools.bridge.SpringAiToolBridge;
import io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent runtime — orchestrates the full execution lifecycle from user input to assistant response.
 * Integrates compaction, hooks, memory, tool bridging, and streaming via SPI interfaces.
 *
 * <p>Tools are resolved lazily from the {@link ToolRegistry} on each {@code run()} call,
 * allowing modules to register tools after construction.
 */
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final SessionManager sessionManager;
    private final ChatClient.Builder chatClientBuilder;
    private final ToolRegistry toolRegistry;
    private final List<SkillDefinition> skills;
    private final Map<String, CompletableFuture<?>> activeTasks = new ConcurrentHashMap<>();

    // Optional SPI collaborators (all nullable)
    private final ChatModel chatModel;
    private final ToolLoopConfig toolLoopConfig;
    private final ContextCompactor compactor;
    private final AgentHookDispatcher hooks;
    private final MemoryProvider memoryProvider;
    private final ToolApprovalHandler approvalHandler;
    private final AgentOrchestrationPort orchestrationPort;

    /**
     * Full constructor with all SPI collaborators.
     */
    public AgentRuntime(
            SessionManager sessionManager,
            ChatClient.Builder chatClientBuilder,
            ToolRegistry toolRegistry,
            List<SkillDefinition> skills,
            ChatModel chatModel,
            ToolLoopConfig toolLoopConfig,
            ContextCompactor compactor,
            AgentHookDispatcher hooks,
            MemoryProvider memoryProvider,
            ToolApprovalHandler approvalHandler,
            AgentOrchestrationPort orchestrationPort) {
        this.sessionManager = sessionManager;
        this.chatClientBuilder = chatClientBuilder;
        this.toolRegistry = toolRegistry;
        this.skills = skills;
        this.chatModel = chatModel;
        this.toolLoopConfig = toolLoopConfig != null ? toolLoopConfig : ToolLoopConfig.DEFAULT;
        this.compactor = compactor;
        this.hooks = hooks;
        this.memoryProvider = memoryProvider;
        this.approvalHandler = approvalHandler;
        this.orchestrationPort = orchestrationPort;
    }

    /**
     * Backward-compatible 4-arg constructor (no SPI collaborators).
     */
    public AgentRuntime(
            SessionManager sessionManager,
            ChatClient.Builder chatClientBuilder,
            ToolRegistry toolRegistry,
            List<SkillDefinition> skills) {
        this(sessionManager, chatClientBuilder, toolRegistry, skills,
                null, null, null, null, null, null, null);
    }

    /**
     * Run the agent asynchronously and return a future with the assistant response.
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

    /**
     * Stream the agent response as a Flux of text chunks. Uses SPRING_AI mode only.
     */
    public Flux<String> runStreaming(String userInput, AgentRuntimeContext context) {
        // 1. Record user message
        UserMessage userMessage = new UserMessage(
                UUID.randomUUID().toString(), userInput, "user");
        sessionManager.appendMessage(context.sessionKey(), userMessage);

        // 2. Resolve tools and build context
        List<ToolCallback> jaiclawTools = toolRegistry.resolveForProfile(context.toolProfile());
        ToolContext toolContext = buildToolContext(context);
        List<org.springframework.ai.tool.ToolCallback> springTools = SpringAiToolBridge.bridgeAll(jaiclawTools, toolContext);

        // 3. Build system prompt with memory
        String systemPrompt = buildSystemPrompt(jaiclawTools, context);

        // 4. Build history
        var session = sessionManager.get(context.sessionKey()).orElse(context.session());
        var historyMessages = session.messages().stream()
                .filter(m -> !(m instanceof UserMessage um && um.id().equals(userMessage.id())))
                .map(this::toSpringAiMessage)
                .toList();

        // 5. Stream via ChatClient
        ChatClient chatClient = chatClientBuilder.build();
        return chatClient.prompt()
                .system(systemPrompt)
                .messages(historyMessages)
                .user(userInput)
                .toolCallbacks(springTools.toArray(new org.springframework.ai.tool.ToolCallback[0]))
                .stream()
                .content();
    }

    private AssistantMessage executeSync(String userInput, AgentRuntimeContext context) {
        // 1. BEFORE_AGENT_START hook
        fireVoid(HookName.BEFORE_AGENT_START, userInput, context.sessionKey());

        // 2. Get/create session
        var session = sessionManager.get(context.sessionKey()).orElse(context.session());

        // 3. Compaction: if compactor != null, compact history before appending new message
        if (compactor != null) {
            var currentMessages = session.messages();
            if (!currentMessages.isEmpty()) {
                List<io.jaiclaw.core.model.Message> compacted = compactor.compactIfNeeded(
                        currentMessages, 128000, prompt -> {
                            // Use ChatClient for summarization LLM call
                            ChatClient client = chatClientBuilder.build();
                            return client.prompt().user(prompt).call().content();
                        });
                if (compacted != currentMessages) {
                    sessionManager.replaceMessages(context.sessionKey(), compacted);
                    session = sessionManager.get(context.sessionKey()).orElse(session);
                }
            }
        }

        // 4. Build history from (possibly compacted) session BEFORE appending current message
        var historyMessages = session.messages().stream()
                .map(this::toSpringAiMessage)
                .toList();

        // 5. Append current UserMessage to session
        UserMessage userMessage = new UserMessage(
                UUID.randomUUID().toString(), userInput, "user");
        sessionManager.appendMessage(context.sessionKey(), userMessage);

        // 6. Resolve tools with proper ToolContext
        List<ToolCallback> jaiclawTools = toolRegistry.resolveForProfile(context.toolProfile());
        ToolContext toolContext = buildToolContext(context);
        List<org.springframework.ai.tool.ToolCallback> springTools = SpringAiToolBridge.bridgeAll(jaiclawTools, toolContext);

        // 7. Build system prompt (identity + skills + tools)
        String systemPrompt = buildSystemPrompt(jaiclawTools, context);

        // 8. Inject memory
        if (memoryProvider != null && context.workspaceDir() != null) {
            String memory = memoryProvider.loadMemory(context.workspaceDir());
            if (memory != null && !memory.isBlank()) {
                systemPrompt = systemPrompt + "\n# Workspace Memory\n\n" + memory + "\n";
            }
        }

        // 9. BEFORE_PROMPT_BUILD modifying hook (allows plugins to alter the prompt)
        systemPrompt = fireModifying(HookName.BEFORE_PROMPT_BUILD, systemPrompt, context.sessionKey());

        // 10. LLM_INPUT hook
        fireVoid(HookName.LLM_INPUT, userInput, context.sessionKey());

        // 11. Embabel orchestration check (future path)
        if (orchestrationPort != null && orchestrationPort.isAvailable()) {
            log.debug("Embabel orchestration available but not yet used for primary flow");
        }

        // 12. Branch on tool loop mode
        String responseContent;
        if (toolLoopConfig.mode() == ToolLoopConfig.Mode.EXPLICIT && chatModel != null) {
            // Explicit loop with hook observability and approval gates
            Map<String, org.springframework.ai.tool.ToolCallback> toolsByName = springTools.stream()
                    .collect(Collectors.toMap(
                            t -> t.getToolDefinition().name(),
                            t -> t,
                            (a, b) -> a));

            ExplicitToolLoop loop = new ExplicitToolLoop(chatModel, toolLoopConfig, hooks, approvalHandler);
            var result = loop.execute(systemPrompt, new ArrayList<>(historyMessages),
                    userInput, toolsByName, context.sessionKey());
            responseContent = result.finalText();
        } else {
            // Spring AI built-in loop (default)
            ChatClient chatClient = chatClientBuilder.build();
            var callSpec = chatClient.prompt()
                    .system(systemPrompt)
                    .messages(historyMessages)
                    .user(userInput)
                    .toolCallbacks(springTools.toArray(new org.springframework.ai.tool.ToolCallback[0]));

            var chatResponse = callSpec.call();
            responseContent = chatResponse.content();
        }

        // 13. LLM_OUTPUT hook
        fireVoid(HookName.LLM_OUTPUT, responseContent, context.sessionKey());

        // 14. Record assistant message in session
        AssistantMessage assistantMessage = new AssistantMessage(
                UUID.randomUUID().toString(),
                responseContent != null ? responseContent : "",
                "default"
        );
        sessionManager.appendMessage(context.sessionKey(), assistantMessage);

        // 15. AGENT_END hook
        fireVoid(HookName.AGENT_END, assistantMessage, context.sessionKey());

        return assistantMessage;
    }

    private String buildSystemPrompt(List<ToolCallback> tools, AgentRuntimeContext context) {
        return new SystemPromptBuilder()
                .tools(tools)
                .skills(skills)
                .identity(context.identity())
                .build();
    }

    private ToolContext buildToolContext(AgentRuntimeContext context) {
        return new ToolContext(
                context.agentId(),
                context.sessionKey(),
                context.session() != null ? context.session().id() : "unknown",
                context.workspaceDir()
        );
    }

    private void fireVoid(HookName hookName, Object event, String sessionKey) {
        if (hooks != null) {
            try {
                hooks.fireVoid(hookName, event, sessionKey);
            } catch (Exception e) {
                log.warn("Hook {} failed: {}", hookName, e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T fireModifying(HookName hookName, T event, String sessionKey) {
        if (hooks != null) {
            try {
                return (T) hooks.fireModifying(hookName, event, sessionKey);
            } catch (Exception e) {
                log.warn("Modifying hook {} failed: {}", hookName, e.getMessage());
            }
        }
        return event;
    }

    private org.springframework.ai.chat.messages.Message toSpringAiMessage(io.jaiclaw.core.model.Message msg) {
        return switch (msg) {
            case io.jaiclaw.core.model.UserMessage u ->
                    new org.springframework.ai.chat.messages.UserMessage(u.content());
            case io.jaiclaw.core.model.AssistantMessage a ->
                    new org.springframework.ai.chat.messages.AssistantMessage(a.content());
            case io.jaiclaw.core.model.SystemMessage s ->
                    new org.springframework.ai.chat.messages.SystemMessage(s.content());
            case io.jaiclaw.core.model.ToolResultMessage t ->
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
