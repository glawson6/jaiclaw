package io.jaiclaw.autoconfigure;

import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.config.TenantAgentConfigService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Gateway auto-configuration — split from {@link JaiClawAutoConfiguration} so that
 * {@code @ConditionalOnBean(AgentRuntime.class)} evaluates <em>after</em> the
 * AgentRuntime bean is defined by the parent auto-config.
 *
 * <p>{@code @AutoConfigureAfter(JaiClawAutoConfiguration.class)} ensures ordering.
 */
@AutoConfiguration
@AutoConfigureAfter(JaiClawAutoConfiguration.class)
@ConditionalOnClass(name = "io.jaiclaw.gateway.GatewayService")
@ConditionalOnBean(AgentRuntime.class)
public class JaiClawGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "io.jaiclaw.gateway.WebhookDispatcher")
    public io.jaiclaw.gateway.WebhookDispatcher webhookDispatcher() {
        return new io.jaiclaw.gateway.WebhookDispatcher();
    }

    @Bean
    @ConditionalOnMissingBean(io.jaiclaw.gateway.tenant.TenantResolver.class)
    public io.jaiclaw.gateway.tenant.CompositeTenantResolver compositeTenantResolver(
            List<io.jaiclaw.gateway.tenant.TenantResolver> resolvers) {
        return new io.jaiclaw.gateway.tenant.CompositeTenantResolver(resolvers);
    }

    @Bean
    @ConditionalOnMissingBean(name = "jwtTenantResolver")
    public io.jaiclaw.gateway.tenant.JwtTenantResolver jwtTenantResolver() {
        return new io.jaiclaw.gateway.tenant.JwtTenantResolver();
    }

    @Bean
    @ConditionalOnMissingBean(name = "botTokenTenantResolver")
    public io.jaiclaw.gateway.tenant.BotTokenTenantResolver botTokenTenantResolver() {
        return new io.jaiclaw.gateway.tenant.BotTokenTenantResolver();
    }

    @Bean
    @ConditionalOnMissingBean(io.jaiclaw.gateway.attachment.AttachmentRouter.class)
    public io.jaiclaw.gateway.attachment.LoggingAttachmentRouter loggingAttachmentRouter() {
        return new io.jaiclaw.gateway.attachment.LoggingAttachmentRouter();
    }

    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.gateway.channel.TenantChannelAdapterRegistry tenantChannelAdapterRegistry() {
        return new io.jaiclaw.gateway.channel.TenantChannelAdapterRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(io.jaiclaw.gateway.mcp.McpServerRegistry.class)
    public io.jaiclaw.gateway.mcp.TenantMcpServerRegistry tenantMcpServerRegistry(
            io.jaiclaw.gateway.mcp.McpServerRegistry globalRegistry) {
        return new io.jaiclaw.gateway.mcp.TenantMcpServerRegistry(globalRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.gateway.GatewayService gatewayService(
            AgentRuntime agentRuntime,
            SessionManager sessionManager,
            ChannelRegistry channelRegistry,
            JaiClawProperties properties,
            io.jaiclaw.gateway.tenant.CompositeTenantResolver tenantResolver,
            io.jaiclaw.gateway.attachment.AttachmentRouter attachmentRouter,
            ObjectProvider<io.jaiclaw.core.tenant.TenantGuard> tenantGuardProvider,
            ObjectProvider<TenantAgentConfigService> configServiceProvider,
            ObjectProvider<io.jaiclaw.gateway.channel.TenantChannelAdapterRegistry> tenantChannelRegistryProvider,
            ObjectProvider<io.jaiclaw.agent.ownership.ThreadOwnershipTracker> ownershipTrackerProvider) {
        return new io.jaiclaw.gateway.GatewayService(
                agentRuntime, sessionManager, channelRegistry,
                properties.agent().defaultAgent(), tenantResolver, attachmentRouter,
                tenantGuardProvider.getIfAvailable(),
                configServiceProvider.getIfAvailable(),
                tenantChannelRegistryProvider.getIfAvailable(),
                ownershipTrackerProvider.getIfAvailable());
    }

    /**
     * Thread ownership tracker — activated when {@code jaiclaw.agent.ownership.enabled=true}.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "jaiclaw.agent.ownership.enabled", havingValue = "true", matchIfMissing = false)
    public io.jaiclaw.agent.ownership.ThreadOwnershipTracker threadOwnershipTracker() {
        return new io.jaiclaw.agent.ownership.ThreadOwnershipTracker();
    }

    /**
     * Creates the gateway lifecycle. When a {@code TelegramUserIdFilter} bean is present
     * (auto-configured from {@code jaiclaw.channels.telegram.allowed-users}), creates a
     * {@link io.jaiclaw.gateway.FilteredGatewayLifecycle} that inserts the filter between
     * channel adapters and the GatewayService. Otherwise, creates the default lifecycle.
     */
    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.gateway.GatewayLifecycle gatewayLifecycle(
            io.jaiclaw.gateway.GatewayService gatewayService,
            ChannelRegistry channelRegistry,
            ObjectProvider<io.jaiclaw.gateway.GatewayMessageFilter> messageFilterProvider) {
        var filter = messageFilterProvider.getIfAvailable();
        if (filter != null) {
            return new io.jaiclaw.gateway.FilteredGatewayLifecycle(
                    gatewayService, channelRegistry, filter);
        }
        return new io.jaiclaw.gateway.GatewayLifecycle(gatewayService);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.gateway.GatewayController gatewayController(
            io.jaiclaw.gateway.GatewayService gatewayService,
            io.jaiclaw.gateway.WebhookDispatcher webhookDispatcher) {
        return new io.jaiclaw.gateway.GatewayController(gatewayService, webhookDispatcher);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.gateway.WebSocketSessionHandler webSocketSessionHandler(
            io.jaiclaw.gateway.GatewayService gatewayService) {
        return new io.jaiclaw.gateway.WebSocketSessionHandler(gatewayService);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.gateway.mcp.McpServerRegistry mcpServerRegistry(
            ObjectProvider<List<io.jaiclaw.core.mcp.McpToolProvider>> toolProviders,
            ObjectProvider<List<io.jaiclaw.core.mcp.McpResourceProvider>> resourceProviders) {
        return new io.jaiclaw.gateway.mcp.McpServerRegistry(
                toolProviders.getIfAvailable(List::of),
                resourceProviders.getIfAvailable(List::of));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(io.jaiclaw.gateway.mcp.McpServerRegistry.class)
    public io.jaiclaw.gateway.mcp.McpController mcpController(
            io.jaiclaw.gateway.mcp.McpServerRegistry registry,
            io.jaiclaw.gateway.tenant.CompositeTenantResolver tenantResolver) {
        return new io.jaiclaw.gateway.mcp.McpController(registry, tenantResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(io.jaiclaw.gateway.mcp.McpServerRegistry.class)
    public io.jaiclaw.gateway.mcp.McpServerConfigBootstrap mcpServerConfigBootstrap(
            JaiClawProperties properties,
            io.jaiclaw.gateway.mcp.McpServerRegistry registry) {
        return new io.jaiclaw.gateway.mcp.McpServerConfigBootstrap(properties, registry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(io.jaiclaw.gateway.mcp.McpServerRegistry.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "jaiclaw.mcp.sse-server.enabled", havingValue = "true", matchIfMissing = true)
    public io.jaiclaw.gateway.mcp.transport.server.McpSseServerController mcpSseServerController(
            io.jaiclaw.gateway.mcp.McpServerRegistry registry,
            io.jaiclaw.gateway.tenant.CompositeTenantResolver tenantResolver,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new io.jaiclaw.gateway.mcp.transport.server.McpSseServerController(registry, tenantResolver, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.gateway.observability.GatewayMetrics gatewayMetrics() {
        return new io.jaiclaw.gateway.observability.GatewayMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.gateway.observability.GatewayHealthIndicator gatewayHealthIndicator(
            ChannelRegistry channelRegistry,
            io.jaiclaw.gateway.observability.GatewayMetrics metrics) {
        return new io.jaiclaw.gateway.observability.GatewayHealthIndicator(channelRegistry, metrics);
    }

    /**
     * Webhook event routing — activated when {@code jaiclaw.webhooks.enabled=true}.
     * Registers a {@link io.jaiclaw.gateway.webhook.WebhookRouteRegistry} and
     * {@link io.jaiclaw.gateway.webhook.WebhookEventController} for dispatching
     * incoming webhook events to registered route handlers.
     */
    @Configuration
    @ConditionalOnProperty(name = "jaiclaw.webhooks.enabled", havingValue = "true", matchIfMissing = false)
    static class WebhookRoutingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jaiclaw.gateway.webhook.WebhookRouteRegistry webhookRouteRegistry() {
            return new io.jaiclaw.gateway.webhook.WebhookRouteRegistry();
        }

        @Bean
        @ConditionalOnMissingBean
        public io.jaiclaw.gateway.webhook.WebhookEventController webhookEventController(
                io.jaiclaw.gateway.webhook.WebhookRouteRegistry registry) {
            return new io.jaiclaw.gateway.webhook.WebhookEventController(registry);
        }
    }

    /**
     * Admin HTTP RPC — activated when {@code jaiclaw.admin.enabled=true}.
     * Provides REST endpoints for session management, channel control,
     * tool listing, and runtime metrics.
     */
    @Configuration
    @ConditionalOnProperty(name = "jaiclaw.admin.enabled", havingValue = "true", matchIfMissing = false)
    static class AdminConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jaiclaw.gateway.admin.AdminController adminController(
                SessionManager sessionManager,
                ChannelRegistry channelRegistry,
                io.jaiclaw.tools.ToolRegistry toolRegistry) {
            return new io.jaiclaw.gateway.admin.AdminController(
                    sessionManager, channelRegistry, toolRegistry);
        }
    }
}
