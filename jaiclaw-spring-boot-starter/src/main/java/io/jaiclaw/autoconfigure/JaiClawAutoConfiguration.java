package io.jaiclaw.autoconfigure;

import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.channel.ChannelAdapter;
import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.core.agent.*;
import io.jaiclaw.core.skill.SkillDefinition;
import io.jaiclaw.memory.InMemorySearchManager;
import io.jaiclaw.memory.MemorySearchManager;
import io.jaiclaw.memory.VectorStoreSearchManager;
import io.jaiclaw.plugin.PluginRegistry;
import io.jaiclaw.skills.SkillLoader;
import io.jaiclaw.tools.ToolRegistry;
import io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort;
import io.jaiclaw.tools.builtin.BuiltinTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

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
@AutoConfigureAfter(name = "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration")
@EnableConfigurationProperties(JaiClawProperties.class)
public class JaiClawAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        var registry = new ToolRegistry();
        BuiltinTools.registerAll(registry);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager() {
        return new SessionManager();
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
    @ConditionalOnMissingBean(MemorySearchManager.class)
    @ConditionalOnClass(name = "org.springframework.ai.vectorstore.VectorStore")
    @ConditionalOnBean(type = "org.springframework.ai.vectorstore.VectorStore")
    public VectorStoreSearchManager vectorStoreSearchManager(
            org.springframework.ai.vectorstore.VectorStore vectorStore) {
        return new VectorStoreSearchManager(vectorStore);
    }

    @Bean
    @ConditionalOnMissingBean(MemorySearchManager.class)
    public InMemorySearchManager inMemorySearchManager() {
        return new InMemorySearchManager();
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
    public MemoryProvider memoryProvider() {
        return new io.jaiclaw.memory.WorkspaceMemoryProvider();
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
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<ContextCompactor> compactorProvider,
            ObjectProvider<AgentHookDispatcher> hooksProvider,
            ObjectProvider<MemoryProvider> memoryProviderProvider,
            ObjectProvider<ToolApprovalHandler> approvalHandlerProvider,
            ObjectProvider<AgentOrchestrationPort> orchestrationPortProvider) {

        List<SkillDefinition> skills = skillLoader.loadConfigured(
                properties.skills().allowBundled(),
                properties.skills().workspaceDir());

        // Resolve tool loop config from properties
        var agentConfig = properties.agent().agents().getOrDefault(
                properties.agent().defaultAgent(),
                io.jaiclaw.config.AgentProperties.AgentConfig.DEFAULT);
        ToolLoopConfig toolLoopConfig = agentConfig.toolLoop().toConfig();

        return new AgentRuntime(
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
                orchestrationPortProvider.getIfAvailable()
        );
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
}
