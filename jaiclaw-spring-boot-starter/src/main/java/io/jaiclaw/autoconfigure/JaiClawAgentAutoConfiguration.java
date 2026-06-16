package io.jaiclaw.autoconfigure;

import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.delegate.AgentLoopDelegate;
import io.jaiclaw.agent.delegate.AgentLoopDelegateRegistry;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.agent.tenant.DefaultTenantChatModelFactory;
import io.jaiclaw.agent.tenant.TenantAgentRuntimeFactory;
import io.jaiclaw.agent.tenant.TenantChatModelFactory;
import io.jaiclaw.channel.ChannelAdapter;
import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.config.CompositeToolProfileRegistry;
import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.config.TenantAgentConfigService;
import io.jaiclaw.config.TenantEnvLoader;
import io.jaiclaw.config.prompt.SystemPromptLoaderFactory;
import io.jaiclaw.core.agent.AgentHookDispatcher;
import io.jaiclaw.core.agent.ContextCompactor;
import io.jaiclaw.core.agent.MemoryProvider;
import io.jaiclaw.core.agent.ToolApprovalHandler;
import io.jaiclaw.core.agent.ToolLoopConfig;
import io.jaiclaw.core.skill.SkillDefinition;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.skills.SkillLoader;
import io.jaiclaw.tools.ToolRegistry;
import io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent runtime, session management, tenant-aware agent factories, and the
 * channel registry.
 *
 * <p>This is the heart of the JaiClaw stack — every other module ultimately
 * exists to feed beans into {@link AgentRuntime}. Beans defined here:
 * <ul>
 *   <li>{@link SessionManager}.</li>
 *   <li>{@link SystemPromptLoaderFactory}, {@link TenantEnvLoader},
 *       {@link TenantAgentConfigService}.</li>
 *   <li>{@link TenantChatModelFactory},
 *       {@link TenantAgentRuntimeFactory}.</li>
 *   <li>{@link AgentLoopDelegateRegistry}.</li>
 *   <li>{@link AgentRuntime} — the singleton entry point.</li>
 *   <li>{@link ChannelRegistry}.</li>
 * </ul>
 *
 * <p>Runs after {@link JaiClawSkillsAutoConfiguration} (and transitively
 * after every other domain auto-config). Also explicitly orders after the
 * Spring AI provider auto-configs so {@link ChatModel} beans are available
 * when {@link TenantChatModelFactory} is created.
 *
 * <p>Carved out of the former {@code JaiClawAutoConfiguration} monolith
 * (audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.4, Phase 3 P3.4).
 */
@AutoConfiguration(after = JaiClawSkillsAutoConfiguration.class)
@AutoConfigureAfter(name = {
        "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration",
        "org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
        "org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration",
        "org.springframework.ai.model.minimax.autoconfigure.MiniMaxChatAutoConfiguration",
        "org.springframework.ai.model.mistral.autoconfigure.MistralChatAutoConfiguration",
        "org.springframework.ai.model.vertexai.autoconfigure.VertexAiGeminiChatAutoConfiguration",
        "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiChatAutoConfiguration",
        "org.springframework.ai.model.bedrock.converse.autoconfigure.BedrockConverseAutoConfiguration"
})
public class JaiClawAgentAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawAgentAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager(TenantGuard tenantGuard) {
        return new SessionManager(tenantGuard);
    }

    // --- Per-tenant configuration beans ---

    @Bean
    @ConditionalOnMissingBean
    public SystemPromptLoaderFactory systemPromptLoaderFactory(ResourceLoader resourceLoader) {
        return new SystemPromptLoaderFactory(resourceLoader);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantEnvLoader tenantEnvLoader(ResourceLoader resourceLoader) {
        return new TenantEnvLoader(resourceLoader);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantAgentConfigService tenantAgentConfigService(JaiClawProperties properties,
                                                              TenantEnvLoader envLoader,
                                                              ResourceLoader resourceLoader,
                                                              org.springframework.core.env.Environment env) {
        io.jaiclaw.config.AgentLoopDelegateConfig loopDelegateOverride =
                resolveLoopDelegateFromEnvironment(properties, env);
        io.jaiclaw.config.LlmConfig llmOverride = resolveLlmFromEnvironment(properties, env);
        io.jaiclaw.config.AgentProperties.ToolPolicyConfig toolsOverride =
                resolveToolsFromEnvironment(properties, env);
        return new TenantAgentConfigService(
                properties.tenant(), properties.agent(), envLoader, resourceLoader,
                loopDelegateOverride, llmOverride, toolsOverride);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ChatModel.class)
    public TenantChatModelFactory tenantChatModelFactory(JaiClawProperties properties,
                                                         ChatModel defaultChatModel) {
        // Default factory delegates to the singleton ChatModel for all tenants.
        // Applications can override with a bean that creates per-tenant models.
        return new DefaultTenantChatModelFactory(properties.models(), request -> defaultChatModel);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({TenantChatModelFactory.class, ChatClient.Builder.class})
    public TenantAgentRuntimeFactory tenantAgentRuntimeFactory(
            TenantChatModelFactory chatModelFactory,
            ToolRegistry toolRegistry,
            SkillLoader skillLoader,
            JaiClawProperties properties,
            SystemPromptLoaderFactory promptLoaderFactory,
            CompositeToolProfileRegistry compositeToolProfileRegistry) {
        List<SkillDefinition> skills = skillLoader.loadConfigured(
                properties.skills().allowBundled(),
                properties.skills().workspaceDir());
        return new TenantAgentRuntimeFactory(
                chatModelFactory, toolRegistry, skills, promptLoaderFactory,
                compositeToolProfileRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentLoopDelegateRegistry agentLoopDelegateRegistry(
            ObjectProvider<List<AgentLoopDelegate>> delegatesProvider) {
        // Use lazy resolution to avoid auto-config ordering issues.
        // Delegates from other auto-configs (e.g., EmbabelDelegateAutoConfiguration)
        // may not be registered yet when this bean is created.
        return new AgentLoopDelegateRegistry(() -> {
            List<AgentLoopDelegate> delegates = delegatesProvider.getIfAvailable();
            return delegates != null ? delegates : List.of();
        });
    }

    // --- AgentRuntime with full SPI wiring ---

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ChatClient.Builder.class)
    public AgentRuntime agentRuntime(
            SessionManager sessionManager,
            ChatClient.Builder chatClientBuilder,
            ToolRegistry toolRegistry,
            SkillLoader skillLoader,
            JaiClawProperties properties,
            TenantGuard tenantGuard,
            CompositeToolProfileRegistry compositeToolProfileRegistry,
            org.springframework.core.env.Environment env,
            SystemPromptLoaderFactory promptLoaderFactory,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<ContextCompactor> compactorProvider,
            ObjectProvider<AgentHookDispatcher> hooksProvider,
            ObjectProvider<MemoryProvider> memoryProviderProvider,
            ObjectProvider<ToolApprovalHandler> approvalHandlerProvider,
            ObjectProvider<AgentOrchestrationPort> orchestrationPortProvider,
            ObjectProvider<TenantAgentRuntimeFactory> tenantRuntimeFactoryProvider,
            ObjectProvider<AgentLoopDelegateRegistry> delegateRegistryProvider) {

        List<SkillDefinition> skills = skillLoader.loadConfigured(
                properties.skills().allowBundled(),
                properties.skills().workspaceDir());

        // Resolve agent config from properties
        Map<String, io.jaiclaw.config.AgentProperties.AgentConfig> agents = properties.agent().agents();
        io.jaiclaw.config.AgentProperties.AgentConfig agentConfig = agents != null
                ? agents.getOrDefault(properties.agent().defaultAgent(),
                        io.jaiclaw.config.AgentProperties.AgentConfig.DEFAULT)
                : io.jaiclaw.config.AgentProperties.AgentConfig.DEFAULT;
        ToolLoopConfig toolLoopConfig = agentConfig.toolLoop().toConfig();

        // Resolve system prompt — first try the bound config, then fall back to Environment
        // (Spring Boot record binding for Map<String, Record> with many fields can silently fail)
        io.jaiclaw.config.SystemPromptConfig systemPromptConfig = agentConfig.systemPrompt();
        if (systemPromptConfig == null) {
            String prefix = "jaiclaw.agent.agents." + properties.agent().defaultAgent() + ".system-prompt";
            String strategy = env.getProperty(prefix + ".strategy");
            String content = env.getProperty(prefix + ".content");
            String source = env.getProperty(prefix + ".source");
            // Resolve if strategy is explicit OR if content/source is present (strategy defaults to "inline")
            if (strategy != null || content != null || source != null) {
                boolean append = Boolean.parseBoolean(env.getProperty(prefix + ".append", "false"));
                systemPromptConfig = new io.jaiclaw.config.SystemPromptConfig(strategy, content, source, append);
                log.info("System prompt resolved from Environment (record binding fallback) — strategy: {}", systemPromptConfig.strategy());
            }
        }

        String additionalInstructions = "";
        boolean replaceSystemPrompt = false;
        if (systemPromptConfig != null) {
            additionalInstructions = promptLoaderFactory.load(systemPromptConfig);
            replaceSystemPrompt = !systemPromptConfig.append();
            log.info("System prompt configured — strategy: {}, replace: {}, length: {}",
                    systemPromptConfig.strategy(), replaceSystemPrompt, additionalInstructions.length());
        } else {
            log.info("No system-prompt configured for agent '{}'", properties.agent().defaultAgent());
        }

        // Resolve tool policy — first try the bound config, then fall back
        // to Environment. We read profile, allow, AND deny from env (not
        // just profile) because Spring's record-binding for the agents map
        // partially-succeeds and silently drops list properties — see
        // docs/issues/tool-allow-deny-env-fallback.md (fixed in 0.9.1).
        io.jaiclaw.config.AgentProperties.ToolPolicyConfig toolPolicy = agentConfig.tools();
        String toolPolicyPrefix = "jaiclaw.agent.agents." + properties.agent().defaultAgent() + ".tools";
        String envProfile = env.getProperty(toolPolicyPrefix + ".profile");
        List<String> envAllow = readStringList(env, toolPolicyPrefix + ".allow");
        List<String> envDeny  = readStringList(env, toolPolicyPrefix + ".deny");
        boolean profileDiffers = envProfile != null && !envProfile.equals(toolPolicy.profile());
        if (profileDiffers || !envAllow.isEmpty() || !envDeny.isEmpty()) {
            toolPolicy = new io.jaiclaw.config.AgentProperties.ToolPolicyConfig(
                    envProfile != null ? envProfile : toolPolicy.profile(),
                    envAllow.isEmpty() ? toolPolicy.allow() : envAllow,
                    envDeny.isEmpty()  ? toolPolicy.deny()  : envDeny);
            log.info("Tool policy resolved from Environment (record binding fallback) — "
                            + "profile: {}, allow: {}, deny: {}",
                    toolPolicy.profile(), toolPolicy.allow(), toolPolicy.deny());
        }
        log.info("Tool policy — profile: {}, allow: {}, deny: {}",
                toolPolicy.profile(), toolPolicy.allow(), toolPolicy.deny());

        AgentRuntime runtime = new AgentRuntime(
                sessionManager,
                chatClientBuilder,
                toolRegistry,
                skills,
                chatModelProvider.getIfAvailable(),
                toolLoopConfig,
                compactorProvider.getIfAvailable(),
                hooksProvider.getIfAvailable(),
                memoryProviderProvider.getIfAvailable(),
                approvalHandlerProvider.getIfAvailable(),
                orchestrationPortProvider.getIfAvailable(),
                tenantRuntimeFactoryProvider.getIfAvailable(),
                delegateRegistryProvider.getIfAvailable(),
                tenantGuard,
                additionalInstructions,
                replaceSystemPrompt,
                toolPolicy,
                compositeToolProfileRegistry
        );

        Set<String> toolNames = toolRegistry.toolNames();
        log.info("AgentRuntime initialized — {} tools available: {}", toolNames.size(), toolNames);
        if (!skills.isEmpty()) {
            log.info("Skills loaded: {}", skills.stream()
                    .map(SkillDefinition::name)
                    .toList());
        }

        return runtime;
    }

    @Bean
    @ConditionalOnMissingBean
    public ChannelRegistry channelRegistry(List<ChannelAdapter> adapters) {
        var registry = new ChannelRegistry();
        adapters.forEach(registry::register);
        return registry;
    }

    /**
     * Resolve loop-delegate config from Environment properties.
     * Spring Boot record binding for {@code Map<String, Record>} with many fields can silently fail
     * for deeply nested records like {@code loop-delegate}. This method reads directly from the
     * Environment as a fallback, matching the pattern used for {@code system-prompt}.
     *
     * @return resolved config, or {@code null} if no loop-delegate properties are set
     */
    private io.jaiclaw.config.AgentLoopDelegateConfig resolveLoopDelegateFromEnvironment(
            JaiClawProperties properties,
            org.springframework.core.env.Environment env) {
        String prefix = "jaiclaw.agent.agents." + properties.agent().defaultAgent() + ".loop-delegate";
        String envEnabled = env.getProperty(prefix + ".enabled");
        String envDelegateId = env.getProperty(prefix + ".delegate-id");

        if (envEnabled == null && envDelegateId == null) {
            return null;
        }

        boolean enabled = Boolean.parseBoolean(envEnabled);
        String workflow = env.getProperty(prefix + ".workflow");
        String llmRole = env.getProperty(prefix + ".llm-role");
        int timeout = Integer.parseInt(env.getProperty(prefix + ".timeout-seconds", "120"));

        io.jaiclaw.config.AgentLoopDelegateConfig config =
                new io.jaiclaw.config.AgentLoopDelegateConfig(
                        enabled, envDelegateId, workflow, llmRole, timeout, Map.of());

        log.info("Loop-delegate resolved from Environment — enabled: {}, delegateId: {}, workflow: {}",
                enabled, envDelegateId, workflow);
        return config;
    }

    /**
     * Resolve LLM config from Environment as a fallback when Spring Boot's record binding
     * silently drops the {@code jaiclaw.agent.agents.<name>.llm} fields.
     */
    private io.jaiclaw.config.LlmConfig resolveLlmFromEnvironment(
            JaiClawProperties properties,
            org.springframework.core.env.Environment env) {
        String prefix = "jaiclaw.agent.agents." + properties.agent().defaultAgent() + ".llm";
        String envPrimary = env.getProperty(prefix + ".primary");

        if (envPrimary == null) {
            return null;
        }

        // Check if record binding already got the right value
        Map<String, io.jaiclaw.config.AgentProperties.AgentConfig> agents = properties.agent().agents();
        io.jaiclaw.config.AgentProperties.AgentConfig agentConfig = agents != null
                ? agents.get(properties.agent().defaultAgent()) : null;
        if (agentConfig != null && agentConfig.llm() != null
                && envPrimary.equals(agentConfig.llm().primary())) {
            return null; // Record binding worked correctly — no override needed
        }

        String envProvider = env.getProperty(prefix + ".provider");
        String envThinkingModel = env.getProperty(prefix + ".thinking-model");
        double temperature = Double.parseDouble(env.getProperty(prefix + ".temperature", "0.7"));
        int maxTokens = Integer.parseInt(env.getProperty(prefix + ".max-tokens", "4096"));
        int timeoutSeconds = Integer.parseInt(env.getProperty(prefix + ".timeout-seconds", "120"));

        io.jaiclaw.config.LlmConfig config = new io.jaiclaw.config.LlmConfig(
                envProvider, envPrimary, List.of(), envThinkingModel,
                temperature, maxTokens, timeoutSeconds);

        log.info("LLM config resolved from Environment (record binding fallback) — provider: {}, primary: {}",
                envProvider, envPrimary);
        return config;
    }

    /**
     * Resolve tools policy config from Environment as a fallback when Spring
     * Boot's record binding silently drops the
     * {@code jaiclaw.agent.agents.<name>.tools} fields.
     *
     * <p>Reads {@code profile}, {@code allow}, and {@code deny} from the
     * environment so YAML-declared allow/deny lists survive a partial
     * record-binding failure. Pre-0.9.1 this method only read
     * {@code profile} and hard-coded the lists to empty — see
     * {@code docs/issues/tool-allow-deny-env-fallback.md}.
     */
    private io.jaiclaw.config.AgentProperties.ToolPolicyConfig resolveToolsFromEnvironment(
            JaiClawProperties properties,
            org.springframework.core.env.Environment env) {
        String prefix = "jaiclaw.agent.agents." + properties.agent().defaultAgent() + ".tools";
        String envProfile = env.getProperty(prefix + ".profile");
        List<String> envAllow = readStringList(env, prefix + ".allow");
        List<String> envDeny  = readStringList(env, prefix + ".deny");

        if (envProfile == null && envAllow.isEmpty() && envDeny.isEmpty()) {
            return null; // nothing in the env — no override to apply
        }

        Map<String, io.jaiclaw.config.AgentProperties.AgentConfig> agents = properties.agent().agents();
        io.jaiclaw.config.AgentProperties.AgentConfig agentConfig = agents != null
                ? agents.get(properties.agent().defaultAgent()) : null;

        // If record binding already got the profile right AND the env doesn't
        // try to override allow/deny, we can let the bound config stand.
        if (envProfile != null
                && agentConfig != null && agentConfig.tools() != null
                && envProfile.equals(agentConfig.tools().profile())
                && envAllow.isEmpty() && envDeny.isEmpty()) {
            return null;
        }

        String effectiveProfile = envProfile != null
                ? envProfile
                : (agentConfig != null && agentConfig.tools() != null
                        ? agentConfig.tools().profile()
                        : "coding");
        List<String> effectiveAllow = !envAllow.isEmpty()
                ? envAllow
                : (agentConfig != null && agentConfig.tools() != null
                        ? agentConfig.tools().allow()
                        : List.of());
        List<String> effectiveDeny = !envDeny.isEmpty()
                ? envDeny
                : (agentConfig != null && agentConfig.tools() != null
                        ? agentConfig.tools().deny()
                        : List.of());

        io.jaiclaw.config.AgentProperties.ToolPolicyConfig config =
                new io.jaiclaw.config.AgentProperties.ToolPolicyConfig(
                        effectiveProfile, effectiveAllow, effectiveDeny);

        log.info("Tools config resolved from Environment (record binding fallback) — "
                        + "profile: {}, allow: {}, deny: {}",
                config.profile(), config.allow(), config.deny());
        return config;
    }

    /**
     * Read a YAML list property from {@link org.springframework.core.env.Environment}.
     * Spring exposes list values as indexed properties at runtime —
     * {@code prefix[0]}, {@code prefix[1]}, … — so this walks those slots
     * until the first {@code null} value, accumulating into a list. Returns
     * an empty list when no slots are populated.
     */
    private static List<String> readStringList(org.springframework.core.env.Environment env, String prefix) {
        List<String> result = new ArrayList<>();
        for (int i = 0; ; i++) {
            String value = env.getProperty(prefix + "[" + i + "]");
            if (value == null) break;
            result.add(value);
        }
        return result;
    }
}
