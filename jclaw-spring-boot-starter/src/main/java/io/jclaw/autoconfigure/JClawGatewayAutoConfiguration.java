package io.jclaw.autoconfigure;

import io.jclaw.agent.AgentRuntime;
import io.jclaw.agent.session.SessionManager;
import io.jclaw.channel.ChannelRegistry;
import io.jclaw.config.JClawProperties;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Gateway auto-configuration — split from {@link JClawAutoConfiguration} so that
 * {@code @ConditionalOnBean(AgentRuntime.class)} evaluates <em>after</em> the
 * AgentRuntime bean is defined by the parent auto-config.
 *
 * <p>{@code @AutoConfigureAfter(JClawAutoConfiguration.class)} ensures ordering.
 */
@AutoConfiguration
@AutoConfigureAfter(JClawAutoConfiguration.class)
@ConditionalOnClass(name = "io.jclaw.gateway.GatewayService")
@ConditionalOnBean(AgentRuntime.class)
public class JClawGatewayAutoConfiguration {

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
    public io.jclaw.gateway.GatewayLifecycle gatewayLifecycle(
            io.jclaw.gateway.GatewayService gatewayService) {
        return new io.jclaw.gateway.GatewayLifecycle(gatewayService);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.jclaw.gateway.GatewayController gatewayController(
            io.jclaw.gateway.GatewayService gatewayService,
            io.jclaw.gateway.WebhookDispatcher webhookDispatcher) {
        return new io.jclaw.gateway.GatewayController(gatewayService, webhookDispatcher);
    }

    @Bean
    @ConditionalOnMissingBean
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
    @ConditionalOnBean(io.jclaw.gateway.mcp.McpServerRegistry.class)
    public io.jclaw.gateway.mcp.McpServerConfigBootstrap mcpServerConfigBootstrap(
            JClawProperties properties,
            io.jclaw.gateway.mcp.McpServerRegistry registry) {
        return new io.jclaw.gateway.mcp.McpServerConfigBootstrap(properties, registry);
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
