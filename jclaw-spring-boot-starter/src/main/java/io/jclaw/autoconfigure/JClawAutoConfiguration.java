package io.jclaw.autoconfigure;

import io.jclaw.agent.AgentRuntime;
import io.jclaw.agent.session.SessionManager;
import io.jclaw.channel.ChannelAdapter;
import io.jclaw.channel.ChannelRegistry;
import io.jclaw.config.JClawProperties;
import io.jclaw.core.skill.SkillDefinition;
import io.jclaw.core.tool.ToolCallback;
import io.jclaw.memory.InMemorySearchManager;
import io.jclaw.memory.MemorySearchManager;
import io.jclaw.memory.VectorStoreSearchManager;
import io.jclaw.plugin.PluginRegistry;
import io.jclaw.skills.SkillLoader;
import io.jclaw.tools.ToolRegistry;
import io.jclaw.tools.builtin.BuiltinTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spring Boot auto-configuration that wires all JClaw modules together.
 * Add {@code jclaw-spring-boot-starter} as a dependency to activate.
 */
@AutoConfiguration
@EnableConfigurationProperties(JClawProperties.class)
public class JClawAutoConfiguration {

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

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ChatClient.Builder.class)
    public AgentRuntime agentRuntime(
            SessionManager sessionManager,
            ChatClient.Builder chatClientBuilder,
            ToolRegistry toolRegistry,
            SkillLoader skillLoader) {
        List<ToolCallback> tools = toolRegistry.resolveAll();
        List<SkillDefinition> skills = skillLoader.loadBundled();
        return new AgentRuntime(sessionManager, chatClientBuilder, tools, skills);
    }

    @Bean
    @ConditionalOnMissingBean
    public ChannelRegistry channelRegistry(List<ChannelAdapter> adapters) {
        var registry = new ChannelRegistry();
        adapters.forEach(registry::register);
        return registry;
    }

    /**
     * Gateway auto-configuration — only active when jclaw-gateway is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.gateway.GatewayService")
    static class GatewayAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnClass(name = "io.jclaw.gateway.WebhookDispatcher")
        public io.jclaw.gateway.WebhookDispatcher webhookDispatcher() {
            return new io.jclaw.gateway.WebhookDispatcher();
        }

        @Bean
        @ConditionalOnMissingBean(io.jclaw.gateway.tenant.TenantResolver.class)
        public io.jclaw.gateway.tenant.CompositeTenantResolver compositeTenantResolver(
                List<io.jclaw.gateway.tenant.TenantResolver> resolvers) {
            return new io.jclaw.gateway.tenant.CompositeTenantResolver(resolvers);
        }

        @Bean
        @ConditionalOnMissingBean(name = "jwtTenantResolver")
        public io.jclaw.gateway.tenant.JwtTenantResolver jwtTenantResolver() {
            return new io.jclaw.gateway.tenant.JwtTenantResolver();
        }

        @Bean
        @ConditionalOnMissingBean(name = "botTokenTenantResolver")
        public io.jclaw.gateway.tenant.BotTokenTenantResolver botTokenTenantResolver() {
            return new io.jclaw.gateway.tenant.BotTokenTenantResolver();
        }

        @Bean
        @ConditionalOnMissingBean(io.jclaw.gateway.attachment.AttachmentRouter.class)
        public io.jclaw.gateway.attachment.LoggingAttachmentRouter loggingAttachmentRouter() {
            return new io.jclaw.gateway.attachment.LoggingAttachmentRouter();
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(AgentRuntime.class)
        public io.jclaw.gateway.GatewayService gatewayService(
                AgentRuntime agentRuntime,
                SessionManager sessionManager,
                ChannelRegistry channelRegistry,
                JClawProperties properties,
                io.jclaw.gateway.tenant.CompositeTenantResolver tenantResolver,
                io.jclaw.gateway.attachment.AttachmentRouter attachmentRouter) {
            return new io.jclaw.gateway.GatewayService(
                    agentRuntime, sessionManager, channelRegistry,
                    properties.agent().defaultAgent(), tenantResolver, attachmentRouter);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.GatewayService.class)
        public io.jclaw.gateway.GatewayLifecycle gatewayLifecycle(
                io.jclaw.gateway.GatewayService gatewayService) {
            return new io.jclaw.gateway.GatewayLifecycle(gatewayService);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.GatewayService.class)
        public io.jclaw.gateway.GatewayController gatewayController(
                io.jclaw.gateway.GatewayService gatewayService,
                io.jclaw.gateway.WebhookDispatcher webhookDispatcher) {
            return new io.jclaw.gateway.GatewayController(gatewayService, webhookDispatcher);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.GatewayService.class)
        public io.jclaw.gateway.WebSocketSessionHandler webSocketSessionHandler(
                io.jclaw.gateway.GatewayService gatewayService) {
            return new io.jclaw.gateway.WebSocketSessionHandler(gatewayService);
        }

        @Bean
        @ConditionalOnMissingBean
        public io.jclaw.gateway.mcp.McpServerRegistry mcpServerRegistry(
                List<io.jclaw.core.mcp.McpToolProvider> providers) {
            return new io.jclaw.gateway.mcp.McpServerRegistry(providers);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.mcp.McpServerRegistry.class)
        public io.jclaw.gateway.mcp.McpController mcpController(
                io.jclaw.gateway.mcp.McpServerRegistry registry,
                io.jclaw.gateway.tenant.CompositeTenantResolver tenantResolver) {
            return new io.jclaw.gateway.mcp.McpController(registry, tenantResolver);
        }

        @Bean
        @ConditionalOnMissingBean
        public io.jclaw.gateway.observability.GatewayMetrics gatewayMetrics() {
            return new io.jclaw.gateway.observability.GatewayMetrics();
        }

        @Bean
        @ConditionalOnMissingBean
        public io.jclaw.gateway.observability.GatewayHealthIndicator gatewayHealthIndicator(
                ChannelRegistry channelRegistry,
                io.jclaw.gateway.observability.GatewayMetrics metrics) {
            return new io.jclaw.gateway.observability.GatewayHealthIndicator(channelRegistry, metrics);
        }
    }

    /**
     * Email adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.email.EmailAdapter")
    static class EmailAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jclaw.channel.email.EmailAdapter emailAdapter() {
            var config = new io.jclaw.channel.email.EmailConfig(
                    System.getenv("EMAIL_PROVIDER"),
                    System.getenv("EMAIL_IMAP_HOST"),
                    parseIntEnv("EMAIL_IMAP_PORT", 993),
                    System.getenv("EMAIL_SMTP_HOST"),
                    parseIntEnv("EMAIL_SMTP_PORT", 587),
                    System.getenv("EMAIL_USERNAME"),
                    System.getenv("EMAIL_PASSWORD"),
                    System.getenv("EMAIL_USERNAME") != null,
                    parseIntEnv("EMAIL_POLL_INTERVAL", 60),
                    null);
            return new io.jclaw.channel.email.EmailAdapter(config);
        }

        private static int parseIntEnv(String key, int defaultValue) {
            String val = System.getenv(key);
            if (val == null || val.isBlank()) return defaultValue;
            try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultValue; }
        }
    }

    /**
     * SMS adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.sms.SmsAdapter")
    static class SmsAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jclaw.channel.sms.SmsAdapter smsAdapter() {
            var config = new io.jclaw.channel.sms.SmsConfig(
                    System.getenv("TWILIO_ACCOUNT_SID"),
                    System.getenv("TWILIO_AUTH_TOKEN"),
                    System.getenv("TWILIO_FROM_NUMBER"),
                    null,
                    System.getenv("TWILIO_ACCOUNT_SID") != null);
            return new io.jclaw.channel.sms.SmsAdapter(config);
        }
    }

    /**
     * Audit auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.audit.AuditLogger")
    static class AuditAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(type = "io.jclaw.audit.AuditLogger")
        public io.jclaw.audit.InMemoryAuditLogger inMemoryAuditLogger() {
            return new io.jclaw.audit.InMemoryAuditLogger();
        }
    }

    /**
     * Orchestration port auto-configuration.
     */
    @Bean
    @ConditionalOnMissingBean(type = "io.jclaw.tools.bridge.embabel.AgentOrchestrationPort")
    public io.jclaw.tools.bridge.embabel.NoOpOrchestrationPort noOpOrchestrationPort() {
        return new io.jclaw.tools.bridge.embabel.NoOpOrchestrationPort();
    }

    /**
     * Telegram adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.telegram.TelegramAdapter")
    static class TelegramAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.WebhookDispatcher.class)
        public io.jclaw.channel.telegram.TelegramAdapter telegramAdapter(
                JClawProperties properties,
                io.jclaw.gateway.WebhookDispatcher webhookDispatcher) {
            // TODO: bind from jclaw.channels.telegram.* properties
            var config = new io.jclaw.channel.telegram.TelegramConfig(
                    System.getenv("TELEGRAM_BOT_TOKEN"),
                    System.getenv("TELEGRAM_WEBHOOK_URL"),
                    System.getenv("TELEGRAM_BOT_TOKEN") != null);
            return new io.jclaw.channel.telegram.TelegramAdapter(config, webhookDispatcher);
        }
    }

    /**
     * Slack adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.slack.SlackAdapter")
    static class SlackAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.WebhookDispatcher.class)
        public io.jclaw.channel.slack.SlackAdapter slackAdapter(
                JClawProperties properties,
                io.jclaw.gateway.WebhookDispatcher webhookDispatcher) {
            var config = new io.jclaw.channel.slack.SlackConfig(
                    System.getenv("SLACK_BOT_TOKEN"),
                    System.getenv("SLACK_SIGNING_SECRET"),
                    System.getenv("SLACK_BOT_TOKEN") != null,
                    System.getenv("SLACK_APP_TOKEN"));
            return new io.jclaw.channel.slack.SlackAdapter(config, webhookDispatcher);
        }
    }

    /**
     * Discord adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.discord.DiscordAdapter")
    static class DiscordAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.WebhookDispatcher.class)
        public io.jclaw.channel.discord.DiscordAdapter discordAdapter(
                JClawProperties properties,
                io.jclaw.gateway.WebhookDispatcher webhookDispatcher) {
            String useGateway = System.getenv("DISCORD_USE_GATEWAY");
            var config = new io.jclaw.channel.discord.DiscordConfig(
                    System.getenv("DISCORD_BOT_TOKEN"),
                    System.getenv("DISCORD_APPLICATION_ID"),
                    System.getenv("DISCORD_BOT_TOKEN") != null,
                    "true".equalsIgnoreCase(useGateway));
            return new io.jclaw.channel.discord.DiscordAdapter(config, webhookDispatcher);
        }
    }
}
