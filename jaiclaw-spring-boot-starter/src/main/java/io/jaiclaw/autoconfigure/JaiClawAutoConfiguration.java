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
import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.config.TenantAgentConfigService;
import io.jaiclaw.config.TenantEnvLoader;
import io.jaiclaw.config.prompt.SystemPromptLoaderFactory;
import io.jaiclaw.core.agent.*;
import io.jaiclaw.core.http.ProxyAwareHttpClientFactory;
import io.jaiclaw.core.http.ProxyAwareHttpClientFactory.ProxyConfig;
import io.jaiclaw.core.skill.SkillDefinition;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantMode;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.memory.InMemorySearchManager;
import io.jaiclaw.memory.MemorySearchManager;
import io.jaiclaw.memory.VectorStoreSearchManager;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginDiscovery;
import io.jaiclaw.plugin.PluginOrigin;
import io.jaiclaw.plugin.PluginRegistry;
import io.jaiclaw.skills.SkillLoader;
import io.jaiclaw.tools.ToolRegistry;
import io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort;
import io.jaiclaw.tools.builtin.BuiltinTools;
import io.jaiclaw.tools.exec.ExecPolicyConfig;
import io.jaiclaw.tools.exec.KubectlPolicyConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core Spring Boot auto-configuration that wires all JaiClaw modules together.
 * Add {@code jaiclaw-spring-boot-starter} as a dependency to activate.
 *
 * <p>Gateway beans are defined in {@link JaiClawGatewayAutoConfiguration} and
 * channel adapters in {@link JaiClawChannelAutoConfiguration}, each with proper
 * {@code @AutoConfigureAfter} ordering so that {@code @ConditionalOnBean}
 * checks resolve reliably.
 */
@AutoConfiguration
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
@EnableConfigurationProperties(JaiClawProperties.class)
public class JaiClawAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawAutoConfiguration.class);

    /**
     * Resolve proxy config from explicit YAML properties or environment variables.
     * Returns null if no proxy is configured.
     */
    private static ProxyConfig resolveProxyConfig(JaiClawProperties properties) {
        var proxyProps = properties.http().proxy();
        if (proxyProps.isConfigured()) {
            return new ProxyConfig(proxyProps.host(), proxyProps.port(),
                    proxyProps.username(), proxyProps.password());
        }
        return ProxyAwareHttpClientFactory.resolveProxy();
    }

    /**
     * Customizes the Spring Boot {@link org.springframework.web.client.RestClient.Builder}
     * with proxy settings. This is picked up by Spring AI's provider auto-configurations
     * (Anthropic, OpenAI, Ollama) which receive a {@code RestClient.Builder} via
     * {@code ObjectProvider} — the builder is a prototype bean that gets customized
     * by all registered {@code RestClientCustomizer} beans before use.
     *
     * <p>Depends on {@link ProxyFactoryConfigurer} to ensure the static factory
     * is configured before this customizer runs.
     */
    @Bean
    RestClientCustomizer proxyRestClientCustomizer(ProxyFactoryConfigurer configurer) {
        return builder -> {
            ProxyConfig resolved = ProxyAwareHttpClientFactory.resolveProxy();
            if (resolved == null) return;

            // JdkClientHttpRequestFactory wraps a java.net.http.HttpClient for use
            // with Spring's RestClient — proxy and auth are baked into the HttpClient
            HttpClient proxyHttpClient = ProxyAwareHttpClientFactory.create();
            builder.requestFactory(new JdkClientHttpRequestFactory(proxyHttpClient));
        };
    }

    /**
     * Configures {@link ProxyAwareHttpClientFactory} for non-Spring HTTP clients
     * (tools, MCP providers, etc.) and sets a global {@link Authenticator} for
     * proxy auth if credentials are provided. Runs at bean construction time so
     * the factory is configured before any tool beans that use it eagerly.
     */
    @Bean
    ProxyFactoryConfigurer proxyFactoryConfigurer(JaiClawProperties properties) {
        return new ProxyFactoryConfigurer(properties);
    }

    /**
     * Configures {@link ProxyAwareHttpClientFactory} and global authenticator
     * as early as possible during bean creation.
     */
    static class ProxyFactoryConfigurer {
        ProxyFactoryConfigurer(JaiClawProperties properties) {
            ProxyConfig resolved = resolveProxyConfig(properties);
            if (resolved == null) return;

            ProxyAwareHttpClientFactory.configure(resolved);

            if (resolved.username() != null && !resolved.username().isBlank()) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                resolved.username(),
                                resolved.password() != null ? resolved.password().toCharArray() : new char[0]);
                    }
                });
            }

            log.info("HTTP proxy configured: {}:{}", resolved.host(), resolved.port());
        }
    }

    // --- Tenant configuration ---

    @Bean
    @ConditionalOnMissingBean
    public TenantProperties tenantProperties(JaiClawProperties properties) {
        var cfg = properties.tenant();
        return new TenantProperties(cfg.mode(), cfg.defaultTenantId());
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantGuard tenantGuard(TenantProperties tenantProperties) {
        TenantGuard guard = new TenantGuard(tenantProperties);
        if (guard.isMultiTenant()) {
            log.info("JaiClaw tenant mode: MULTI — strict isolation enabled");
        } else {
            log.info("JaiClaw tenant mode: SINGLE — shared data space");
        }
        return guard;
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(JaiClawProperties properties) {
        var registry = new ToolRegistry();
        ExecPolicyConfig execPolicyConfig = toExecPolicyConfig(properties.tools().exec());
        boolean ssrfProtection = properties.tools().web().ssrfProtection();
        BuiltinTools.registerAll(registry, execPolicyConfig, ssrfProtection);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public KubectlPolicyConfig kubectlPolicyConfig(JaiClawProperties properties) {
        return toKubectlPolicyConfig(properties.tools().exec().kubectl());
    }

    private static ExecPolicyConfig toExecPolicyConfig(
            io.jaiclaw.config.ToolsProperties.ExecToolProperties props) {
        return new ExecPolicyConfig(
                props.policy(),
                props.allowedCommands(),
                props.blockedPatterns(),
                props.maxTimeout()
        );
    }

    private static KubectlPolicyConfig toKubectlPolicyConfig(
            io.jaiclaw.config.ToolsProperties.KubectlPolicyProperties props) {
        return new KubectlPolicyConfig(
                props.policy(),
                props.allowedVerbs(),
                props.blockedVerbs()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager(TenantGuard tenantGuard) {
        return new SessionManager(tenantGuard);
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillLoader skillLoader() {
        return new SkillLoader();
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginRegistry pluginRegistry() {
        return new PluginRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginDiscovery pluginDiscovery(ToolRegistry toolRegistry,
                                            PluginRegistry pluginRegistry,
                                            ObjectProvider<List<JaiClawPlugin>> pluginsProvider) {
        PluginDiscovery discovery = new PluginDiscovery(toolRegistry, pluginRegistry);

        // Initialize Spring-managed JaiClawPlugin beans
        List<JaiClawPlugin> plugins = pluginsProvider.getIfAvailable();
        if (plugins != null && !plugins.isEmpty()) {
            discovery.initializeAll(plugins, PluginOrigin.SPRING);
        }

        // Discover additional plugins via ServiceLoader
        discovery.discoverServiceLoader();

        return discovery;
    }

    @Bean
    @ConditionalOnMissingBean(MemorySearchManager.class)
    @ConditionalOnClass(name = "org.springframework.ai.vectorstore.VectorStore")
    @ConditionalOnBean(type = "org.springframework.ai.vectorstore.VectorStore")
    public VectorStoreSearchManager vectorStoreSearchManager(
            org.springframework.ai.vectorstore.VectorStore vectorStore,
            TenantGuard tenantGuard) {
        return new VectorStoreSearchManager(vectorStore, tenantGuard);
    }

    @Bean
    @ConditionalOnMissingBean(MemorySearchManager.class)
    public InMemorySearchManager inMemorySearchManager(TenantGuard tenantGuard) {
        return new InMemorySearchManager(tenantGuard);
    }

    // --- SPI adapter beans ---

    @Bean
    @ConditionalOnMissingBean(AgentHookDispatcher.class)
    @ConditionalOnClass(name = "io.jaiclaw.plugin.HookRunnerAdapter")
    public AgentHookDispatcher agentHookDispatcher(PluginRegistry pluginRegistry) {
        var hookRunner = new io.jaiclaw.plugin.HookRunner(pluginRegistry);
        return new io.jaiclaw.plugin.HookRunnerAdapter(hookRunner);
    }

    @Bean
    @ConditionalOnMissingBean(ContextCompactor.class)
    @ConditionalOnClass(name = "io.jaiclaw.compaction.CompactionServiceAdapter")
    public ContextCompactor contextCompactor() {
        var config = io.jaiclaw.core.model.CompactionConfig.DEFAULT;
        var service = new io.jaiclaw.compaction.CompactionService(config);
        return new io.jaiclaw.compaction.CompactionServiceAdapter(service);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryProvider.class)
    @ConditionalOnClass(name = "io.jaiclaw.memory.WorkspaceMemoryProvider")
    public MemoryProvider memoryProvider(TenantGuard tenantGuard) {
        return new io.jaiclaw.memory.WorkspaceMemoryProvider(tenantGuard);
    }

    // --- Per-tenant configuration beans ---

    @Bean
    @ConditionalOnMissingBean
    public SystemPromptLoaderFactory systemPromptLoaderFactory() {
        return new SystemPromptLoaderFactory();
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
        return new TenantAgentConfigService(
                properties.tenant(), properties.agent(), envLoader, resourceLoader, loopDelegateOverride);
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
            SystemPromptLoaderFactory promptLoaderFactory) {
        List<SkillDefinition> skills = skillLoader.loadConfigured(
                properties.skills().allowBundled(),
                properties.skills().workspaceDir());
        return new TenantAgentRuntimeFactory(
                chatModelFactory, toolRegistry, skills, promptLoaderFactory);
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
            org.springframework.core.env.Environment env,
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
            if (strategy != null) {
                String content = env.getProperty(prefix + ".content");
                String source = env.getProperty(prefix + ".source");
                boolean append = Boolean.parseBoolean(env.getProperty(prefix + ".append", "false"));
                systemPromptConfig = new io.jaiclaw.config.SystemPromptConfig(strategy, content, source, append);
                log.info("System prompt resolved from Environment (record binding fallback) — strategy: {}", strategy);
            }
        }

        String additionalInstructions = "";
        boolean replaceSystemPrompt = false;
        if (systemPromptConfig != null) {
            SystemPromptLoaderFactory promptLoader = new SystemPromptLoaderFactory();
            additionalInstructions = promptLoader.load(systemPromptConfig);
            replaceSystemPrompt = !systemPromptConfig.append();
            log.info("System prompt configured — strategy: {}, replace: {}, length: {}",
                    systemPromptConfig.strategy(), replaceSystemPrompt, additionalInstructions.length());
        } else {
            log.info("No system-prompt configured for agent '{}'", properties.agent().defaultAgent());
        }

        // Resolve tool policy — first try the bound config, then fall back to Environment
        io.jaiclaw.config.AgentProperties.ToolPolicyConfig toolPolicy = agentConfig.tools();
        String toolPolicyPrefix = "jaiclaw.agent.agents." + properties.agent().defaultAgent() + ".tools";
        String envProfile = env.getProperty(toolPolicyPrefix + ".profile");
        if (envProfile != null && !envProfile.equals(toolPolicy.profile())) {
            toolPolicy = new io.jaiclaw.config.AgentProperties.ToolPolicyConfig(
                    envProfile, toolPolicy.allow(), toolPolicy.deny());
            log.info("Tool policy resolved from Environment (record binding fallback) — profile: {}", envProfile);
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
                toolPolicy
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
     * Orchestration port auto-configuration.
     */
    @Bean
    @ConditionalOnMissingBean(type = "io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort")
    public io.jaiclaw.tools.bridge.embabel.NoOpOrchestrationPort noOpOrchestrationPort() {
        return new io.jaiclaw.tools.bridge.embabel.NoOpOrchestrationPort();
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
}
