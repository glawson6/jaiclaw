package io.jaiclaw.autoconfigure;

import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.config.JaiClawProperties;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

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
    public io.jaiclaw.gateway.GatewayService gatewayService(
            AgentRuntime agentRuntime,
            SessionManager sessionManager,
            ChannelRegistry channelRegistry,
            JaiClawProperties properties,
            io.jaiclaw.gateway.tenant.CompositeTenantResolver tenantResolver,
            io.jaiclaw.gateway.attachment.AttachmentRouter attachmentRouter) {
        return new io.jaiclaw.gateway.GatewayService(
                agentRuntime, sessionManager, channelRegistry,
                properties.agent().defaultAgent(), tenantResolver, attachmentRouter);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.gateway.GatewayLifecycle gatewayLifecycle(
            io.jaiclaw.gateway.GatewayService gatewayService) {
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
            List<io.jaiclaw.core.mcp.McpToolProvider> providers) {
        return new io.jaiclaw.gateway.mcp.McpServerRegistry(providers);
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
}
