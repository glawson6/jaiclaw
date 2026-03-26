package io.jaiclaw.cronmanager;

import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.gateway.mcp.McpController;
import io.jaiclaw.gateway.mcp.McpServerRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Provides MCP hosting beans when running standalone (no gateway).
 * When embedded in the gateway, {@code JaiClawGatewayAutoConfiguration} already
 * provides these beans, so {@code @ConditionalOnMissingBean} backs off.
 */
@Configuration
@ConditionalOnMissingBean(McpServerRegistry.class)
public class StandaloneCronManagerConfiguration {

    @Bean
    public McpServerRegistry mcpServerRegistry(List<McpToolProvider> providers) {
        return new McpServerRegistry(providers);
    }

    @Bean
    public McpController mcpController(McpServerRegistry registry) {
        return new McpController(registry);
    }
}
