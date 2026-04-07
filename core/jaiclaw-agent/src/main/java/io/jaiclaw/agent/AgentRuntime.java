package io.jaiclaw.agent;

import io.jaiclaw.agent.delegate.AgentLoopDelegate;
import io.jaiclaw.agent.delegate.AgentLoopDelegateContext;
import io.jaiclaw.agent.delegate.AgentLoopDelegateRegistry;
import io.jaiclaw.agent.delegate.AgentLoopDelegateResult;
import io.jaiclaw.agent.loop.ExplicitToolLoop;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.agent.tenant.TenantAgentExecutionContext;
import io.jaiclaw.agent.tenant.TenantAgentRuntimeFactory;
import io.jaiclaw.config.AgentProperties;
import io.jaiclaw.config.TenantAgentConfig;
import io.jaiclaw.core.agent.*;
import io.jaiclaw.core.hook.HookName;
import io.jaiclaw.core.model.AssistantMessage;
import io.jaiclaw.core.model.TokenUsage;
import io.jaiclaw.core.model.UserMessage;
import io.jaiclaw.core.skill.SkillDefinition;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.tools.ToolRegistry;
import io.jaiclaw.tools.bridge.SpringAiToolBridge;
import io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import io.jaiclaw.core.tenant.TenantContextPropagator;
import io.jaiclaw.core.tenant.TenantGuard;

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
    private final String defaultAdditionalInstructions;
    private final boolean replaceSystemPrompt;

    // Deployment-time tool policy from YAML (profile + allow/deny)
    private final AgentProperties.ToolPolicyConfig defaultToolPolicy;

    // Per-tenant support (nullable — null means singleton path)
    private final TenantAgentRuntimeFactory tenantRuntimeFactory;
    private final AgentLoopDelegateRegistry delegateRegistry;
    private final TenantGuard tenantGuard;

    public static Builder builder() { return new Builder(); }

    /**
     * Full constructor with all SPI collaborators and per-tenant support.
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
            AgentOrchestrationPort orchestrationPort,
            TenantAgentRuntimeFactory tenantRuntimeFactory,
            AgentLoopDelegateRegistry delegateRegistry,
            TenantGuard tenantGuard,
            String defaultAdditionalInstructions,
            boolean replaceSystemPrompt,
            AgentProperties.ToolPolicyConfig defaultToolPolicy) {
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
        this.tenantRuntimeFactory = tenantRuntimeFactory;
        this.delegateRegistry = delegateRegistry;
        this.tenantGuard = tenantGuard;
        this.defaultAdditionalInstructions = defaultAdditionalInstructions != null ? defaultAdditionalInstructions : "";
        this.replaceSystemPrompt = replaceSystemPrompt;
        this.defaultToolPolicy = defaultToolPolicy != null ? defaultToolPolicy : AgentProperties.ToolPolicyConfig.DEFAULT;
    }

    /**
     * Backward-compatible full constructor without additional instructions.
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
            AgentOrchestrationPort orchestrationPort,
            TenantAgentRuntimeFactory tenantRuntimeFactory,
            AgentLoopDelegateRegistry delegateRegistry,
            TenantGuard tenantGuard) {
        this(sessionManager, chatClientBuilder, toolRegistry, skills,
                chatModel, toolLoopConfig, compactor, hooks, memoryProvider,
                approvalHandler, orchestrationPort, tenantRuntimeFactory,
                delegateRegistry, tenantGuard, null, false, null);
    }

    /**
     * Constructor with all SPI collaborators (backward-compatible, no per-tenant support).
     */
    @Deprecated
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
        this(sessionManager, chatClientBuilder, toolRegistry, skills,
                chatModel, toolLoopConfig, compactor, hooks, memoryProvider,
                approvalHandler, orchestrationPort, null, null, null);
    }

    /**
     * Backward-compatible 4-arg constructor (no SPI collaborators).
     */
    @Deprecated
    public AgentRuntime(
            SessionManager sessionManager,
            ChatClient.Builder chatClientBuilder,
            ToolRegistry toolRegistry,
            List<SkillDefinition> skills) {
        this(sessionManager, chatClientBuilder, toolRegistry, skills,
                null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Run the agent asynchronously and return a future with the assistant response.
     */
    public CompletableFuture<AssistantMessage> run(String userInput, AgentRuntimeContext context) {
        var future = CompletableFuture.supplyAsync(TenantContextPropagator.wrap(() -> {
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
        }));

        String taskKey = scopedTaskKey(context.sessionKey());
        activeTasks.put(taskKey, future);
        future.whenComplete((r, e) -> activeTasks.remove(taskKey));
        return future;
    }

    /**
     * Stream the agent response as a Flux of text chunks. Uses SPRING_AI mode only.
     */
    public Flux<String> runStreaming(String userInput, AgentRuntimeContext context) {
        // Check delegate first for per-tenant config
        if (context.tenantConfig() != null && delegateRegistry != null) {
            Optional<AgentLoopDelegate> delegate = delegateRegistry.resolve(context.tenantConfig());
            if (delegate.isPresent()) {
                log.info("Streaming via delegate '{}' for tenant: {}",
                        delegate.get().delegateId(), context.tenantConfig().tenantId());
                String systemPrompt = resolveTenantSystemPrompt(context);
                AgentLoopDelegateContext delegateCtx = buildDelegateContext(context, systemPrompt);
                return delegate.get().executeStream(userInput, delegateCtx);
            }
        }

        // 1. Record user message
        UserMessage userMessage = new UserMessage(
                UUID.randomUUID().toString(), userInput, "user");
        if (!context.stateless()) {
            sessionManager.appendMessage(context.sessionKey(), userMessage);
        }

        // 2. Resolve tools, prompt, and ChatClient — tenant-aware or singleton
        List<ToolCallback> jaiclawTools;
        String systemPrompt;
        ChatClient.Builder effectiveClientBuilder;

        if (context.tenantConfig() != null && tenantRuntimeFactory != null) {
            TenantAgentExecutionContext tenantCtx = tenantRuntimeFactory.createContext(context.tenantConfig());
            jaiclawTools = tenantCtx.resolvedTools();
            systemPrompt = tenantCtx.resolvedSystemPrompt();
            effectiveClientBuilder = tenantCtx.chatClientBuilder();
        } else {
            jaiclawTools = resolveToolsForSingleton();
            systemPrompt = buildSystemPrompt(jaiclawTools, context);
            effectiveClientBuilder = chatClientBuilder;
        }

        ToolContext toolContext = buildToolContext(context);
        List<org.springframework.ai.tool.ToolCallback> springTools = SpringAiToolBridge.bridgeAll(jaiclawTools, toolContext);

        // 3. Build history
        var session = sessionManager.get(context.sessionKey()).orElse(context.session());
        var historyMessages = session.messages().stream()
                .filter(m -> !(m instanceof UserMessage um && um.id().equals(userMessage.id())))
                .map(this::toSpringAiMessage)
                .toList();

        // 4. Stream via ChatClient
        ChatClient chatClient = effectiveClientBuilder.build();
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

        // 1a. Check if an agent loop delegate should handle this tenant
        if (context.tenantConfig() != null && delegateRegistry != null) {
            Optional<AgentLoopDelegate> delegate = delegateRegistry.resolve(context.tenantConfig());
            if (delegate.isPresent()) {
                log.info("Routing to agent delegate '{}' for tenant: {}",
                        delegate.get().delegateId(), context.tenantConfig().tenantId());
                String systemPrompt = resolveTenantSystemPrompt(context);
                AgentLoopDelegateContext delegateCtx = buildDelegateContext(context, systemPrompt);
                AgentLoopDelegateResult result = delegate.get().execute(userInput, delegateCtx);

                AssistantMessage assistantMessage = new AssistantMessage(
                        UUID.randomUUID().toString(),
                        result.content() != null ? result.content() : "",
                        "default"
                );
                if (!context.stateless()) {
                    sessionManager.appendMessage(context.sessionKey(), assistantMessage);
                }
                fireVoid(HookName.AGENT_END, assistantMessage, context.sessionKey());
                return assistantMessage;
            }
        }

        // 2. Get/create session
        var session = context.stateless()
                ? context.session()
                : sessionManager.get(context.sessionKey()).orElse(context.session());

        // 3. Compaction: if compactor != null, compact history before appending new message
        if (!context.stateless() && compactor != null) {
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
        if (!context.stateless()) {
            sessionManager.appendMessage(context.sessionKey(), userMessage);
        }

        // 6. Resolve tools and system prompt — tenant-aware or singleton
        List<ToolCallback> jaiclawTools;
        String systemPrompt;
        ChatClient.Builder effectiveClientBuilder;
        ChatModel effectiveChatModel;
        ToolLoopConfig effectiveToolLoopConfig;

        if (context.tenantConfig() != null && tenantRuntimeFactory != null) {
            TenantAgentExecutionContext tenantCtx = tenantRuntimeFactory.createContext(context.tenantConfig());
            jaiclawTools = tenantCtx.resolvedTools();
            systemPrompt = tenantCtx.resolvedSystemPrompt();
            effectiveClientBuilder = tenantCtx.chatClientBuilder();
            effectiveChatModel = tenantCtx.chatModel();
            effectiveToolLoopConfig = context.tenantConfig().toolLoop() != null
                    ? context.tenantConfig().toolLoop().toConfig()
                    : toolLoopConfig;
        } else {
            jaiclawTools = resolveToolsForSingleton();
            systemPrompt = buildSystemPrompt(jaiclawTools, context);
            effectiveClientBuilder = chatClientBuilder;
            effectiveChatModel = chatModel;
            effectiveToolLoopConfig = toolLoopConfig;
        }

        ToolContext toolContext = buildToolContext(context);
        List<org.springframework.ai.tool.ToolCallback> springTools = SpringAiToolBridge.bridgeAll(jaiclawTools, toolContext);

        // 7. Inject memory (only for singleton path; tenant prompt already resolved)
        if (context.tenantConfig() == null && memoryProvider != null && context.workspaceDir() != null) {
            String memory = memoryProvider.loadMemory(context.workspaceDir());
            if (memory != null && !memory.isBlank()) {
                systemPrompt = systemPrompt + "\n# Workspace Memory\n\n" + memory + "\n";
            }
        }

        // 8. BEFORE_PROMPT_BUILD modifying hook (allows plugins to alter the prompt)
        systemPrompt = fireModifying(HookName.BEFORE_PROMPT_BUILD, systemPrompt, context.sessionKey());

        // 9. LLM_INPUT hook
        fireVoid(HookName.LLM_INPUT, userInput, context.sessionKey());

        // 10. Embabel orchestration check (future path)
        if (orchestrationPort != null && orchestrationPort.isAvailable()) {
            log.debug("Embabel orchestration available but not yet used for primary flow");
        }

        // 11. Branch on tool loop mode
        long llmStartNanos = System.nanoTime();
        String responseContent;
        TokenUsage tokenUsage = TokenUsage.ZERO;
        if (effectiveToolLoopConfig.mode() == ToolLoopConfig.Mode.EXPLICIT && effectiveChatModel != null) {
            // Explicit loop with hook observability and approval gates
            Map<String, org.springframework.ai.tool.ToolCallback> toolsByName = springTools.stream()
                    .collect(Collectors.toMap(
                            t -> t.getToolDefinition().name(),
                            t -> t,
                            (a, b) -> a));

            ExplicitToolLoop loop = new ExplicitToolLoop(effectiveChatModel, effectiveToolLoopConfig, hooks, approvalHandler);
            var result = loop.execute(systemPrompt, new ArrayList<>(historyMessages),
                    userInput, toolsByName, context.sessionKey());
            responseContent = result.finalText();
            tokenUsage = result.totalUsage();
        } else {
            // Spring AI built-in loop (default)
            ChatClient chatClient = effectiveClientBuilder.build();
            var callSpec = chatClient.prompt()
                    .system(systemPrompt)
                    .messages(historyMessages)
                    .user(userInput)
                    .toolCallbacks(springTools.toArray(new org.springframework.ai.tool.ToolCallback[0]));

            var chatResponse = callSpec.call().chatResponse();
            responseContent = chatResponse != null && chatResponse.getResult() != null
                    ? chatResponse.getResult().getOutput().getText() : null;
            tokenUsage = ExplicitToolLoop.extractUsage(chatResponse);

            LlmTraceLogger.logRequest(systemPrompt, new ArrayList<>(historyMessages),
                    userInput, springTools, tokenUsage.inputTokens());
            LlmTraceLogger.logResponse(responseContent, tokenUsage.outputTokens());
        }

        long llmElapsedMs = (System.nanoTime() - llmStartNanos) / 1_000_000;

        // 11a. Sanitize: strip chain-of-thought reasoning leaked by some models
        responseContent = ResponseSanitizer.sanitize(responseContent);
        if (ResponseSanitizer.isEntirelyReasoning(responseContent)) {
            log.warn("Response is entirely chain-of-thought reasoning — retrying with stronger instruction");
            ChatClient retryClient = (effectiveClientBuilder != null ? effectiveClientBuilder : chatClientBuilder).build();
            var retryResponse = retryClient.prompt()
                    .system("You are a conversational assistant. Respond DIRECTLY to the user. " +
                            "Do NOT describe what you will do. Do NOT explain your reasoning. " +
                            "Just give the actual response the user should see.")
                    .user(userInput)
                    .call()
                    .chatResponse();
            if (retryResponse != null && retryResponse.getResult() != null) {
                String retryContent = retryResponse.getResult().getOutput().getText();
                if (retryContent != null && !ResponseSanitizer.isEntirelyReasoning(retryContent)) {
                    responseContent = retryContent;
                    TokenUsage retryUsage = ExplicitToolLoop.extractUsage(retryResponse);
                    tokenUsage = tokenUsage.add(retryUsage);
                }
            }
        }

        // 11b. Log token usage and duration
        log.info("LLM usage — {} ms, request: {} tokens, response: {} tokens, total: {} tokens",
                llmElapsedMs, tokenUsage.inputTokens(), tokenUsage.outputTokens(), tokenUsage.totalTokens());
        if (tokenUsage.cacheReadTokens() > 0 || tokenUsage.cacheWriteTokens() > 0) {
            log.info("LLM cache — read: {} tokens, write: {} tokens",
                    tokenUsage.cacheReadTokens(), tokenUsage.cacheWriteTokens());
        }

        // 12. LLM_OUTPUT hook
        fireVoid(HookName.LLM_OUTPUT, responseContent, context.sessionKey());

        // 13. Record assistant message in session
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .id(UUID.randomUUID().toString())
                .content(responseContent != null ? responseContent : "")
                .modelId("default")
                .usage(tokenUsage)
                .metadata(Map.of("llmDurationMs", llmElapsedMs))
                .build();
        if (!context.stateless()) {
            sessionManager.appendMessage(context.sessionKey(), assistantMessage);
        }

        // 14. AGENT_END hook
        fireVoid(HookName.AGENT_END, assistantMessage, context.sessionKey());

        return assistantMessage;
    }

    private String resolveTenantSystemPrompt(AgentRuntimeContext context) {
        if (context.tenantConfig() != null && tenantRuntimeFactory != null) {
            TenantAgentExecutionContext tenantCtx = tenantRuntimeFactory.createContext(context.tenantConfig());
            return tenantCtx.resolvedSystemPrompt();
        }
        return buildSystemPrompt(resolveToolsForSingleton(), context);
    }

    private AgentLoopDelegateContext buildDelegateContext(AgentRuntimeContext context, String systemPrompt) {
        TenantAgentConfig cfg = context.tenantConfig();
        var session = sessionManager.get(context.sessionKey()).orElse(context.session());
        String history = session != null && session.messages() != null
                ? session.messages().stream()
                    .map(m -> messageRole(m) + ": " + m.content())
                    .collect(Collectors.joining("\n"))
                : "";

        Map<String, Object> props = cfg.loopDelegate() != null && cfg.loopDelegate().properties() != null
                ? new HashMap<>(cfg.loopDelegate().properties())
                : Map.of();

        return new AgentLoopDelegateContext(
                context.sessionKey(),
                cfg.tenantId(),
                history,
                cfg,
                systemPrompt,
                props
        );
    }

    /**
     * Resolve tools for the singleton (non-tenant) path using the deployment-time tool policy.
     */
    private List<ToolCallback> resolveToolsForSingleton() {
        ToolProfile profile = resolveProfile(defaultToolPolicy.profile());
        return toolRegistry.resolveForPolicy(profile, defaultToolPolicy.allow(), defaultToolPolicy.deny());
    }

    private static ToolProfile resolveProfile(String profileName) {
        if (profileName == null) return ToolProfile.FULL;
        try {
            return ToolProfile.valueOf(profileName.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown tool profile '{}', defaulting to FULL", profileName);
            return ToolProfile.FULL;
        }
    }

    private String buildSystemPrompt(List<ToolCallback> tools, AgentRuntimeContext context) {
        if (replaceSystemPrompt && !defaultAdditionalInstructions.isEmpty()) {
            return defaultAdditionalInstructions;
        }
        return new SystemPromptBuilder()
                .tools(tools)
                .skills(skills)
                .identity(context.identity())
                .additionalInstructions(defaultAdditionalInstructions)
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

    private static String messageRole(io.jaiclaw.core.model.Message msg) {
        return switch (msg) {
            case io.jaiclaw.core.model.UserMessage u -> "user";
            case io.jaiclaw.core.model.AssistantMessage a -> "assistant";
            case io.jaiclaw.core.model.SystemMessage s -> "system";
            case io.jaiclaw.core.model.ToolResultMessage t -> "tool";
        };
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
        String key = scopedTaskKey(sessionKey);
        CompletableFuture<?> task = activeTasks.get(key);
        if (task != null) {
            task.cancel(true);
            activeTasks.remove(key);
        }
    }

    public boolean isRunning(String sessionKey) {
        String key = scopedTaskKey(sessionKey);
        CompletableFuture<?> task = activeTasks.get(key);
        return task != null && !task.isDone();
    }

    /**
     * Scope task keys by tenant in MULTI mode to prevent cross-tenant collisions.
     */
    private String scopedTaskKey(String sessionKey) {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String prefix = tenantGuard.resolveTenantPrefix();
            if (!sessionKey.startsWith(prefix + ":")) {
                return prefix + ":" + sessionKey;
            }
        }
        return sessionKey;
    }

    public static final class Builder {
        private SessionManager sessionManager;
        private ChatClient.Builder chatClientBuilder;
        private ToolRegistry toolRegistry;
        private List<SkillDefinition> skills;
        private ChatModel chatModel;
        private ToolLoopConfig toolLoopConfig;
        private ContextCompactor compactor;
        private AgentHookDispatcher hooks;
        private MemoryProvider memoryProvider;
        private ToolApprovalHandler approvalHandler;
        private AgentOrchestrationPort orchestrationPort;
        private TenantAgentRuntimeFactory tenantRuntimeFactory;
        private AgentLoopDelegateRegistry delegateRegistry;
        private TenantGuard tenantGuard;
        private AgentProperties.ToolPolicyConfig defaultToolPolicy;

        public Builder sessionManager(SessionManager sessionManager) { this.sessionManager = sessionManager; return this; }
        public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) { this.chatClientBuilder = chatClientBuilder; return this; }
        public Builder toolRegistry(ToolRegistry toolRegistry) { this.toolRegistry = toolRegistry; return this; }
        public Builder skills(List<SkillDefinition> skills) { this.skills = skills; return this; }
        public Builder chatModel(ChatModel chatModel) { this.chatModel = chatModel; return this; }
        public Builder toolLoopConfig(ToolLoopConfig toolLoopConfig) { this.toolLoopConfig = toolLoopConfig; return this; }
        public Builder compactor(ContextCompactor compactor) { this.compactor = compactor; return this; }
        public Builder hooks(AgentHookDispatcher hooks) { this.hooks = hooks; return this; }
        public Builder memoryProvider(MemoryProvider memoryProvider) { this.memoryProvider = memoryProvider; return this; }
        public Builder approvalHandler(ToolApprovalHandler approvalHandler) { this.approvalHandler = approvalHandler; return this; }
        public Builder orchestrationPort(AgentOrchestrationPort orchestrationPort) { this.orchestrationPort = orchestrationPort; return this; }
        public Builder tenantRuntimeFactory(TenantAgentRuntimeFactory tenantRuntimeFactory) { this.tenantRuntimeFactory = tenantRuntimeFactory; return this; }
        public Builder delegateRegistry(AgentLoopDelegateRegistry delegateRegistry) { this.delegateRegistry = delegateRegistry; return this; }
        public Builder tenantGuard(TenantGuard tenantGuard) { this.tenantGuard = tenantGuard; return this; }
        public Builder defaultToolPolicy(AgentProperties.ToolPolicyConfig defaultToolPolicy) { this.defaultToolPolicy = defaultToolPolicy; return this; }

        public AgentRuntime build() {
            return new AgentRuntime(sessionManager, chatClientBuilder, toolRegistry, skills,
                    chatModel, toolLoopConfig, compactor, hooks, memoryProvider,
                    approvalHandler, orchestrationPort, tenantRuntimeFactory, delegateRegistry, tenantGuard,
                    null, false, defaultToolPolicy);
        }
    }
}
