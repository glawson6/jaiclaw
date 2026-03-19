package io.jclaw.gateway.mcp;

import io.jclaw.config.JClawProperties;
import io.jclaw.config.McpServerProperties;
import io.jclaw.core.mcp.McpToolProvider;
import io.jclaw.gateway.mcp.transport.McpTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.Map;

/**
 * Bootstraps config-driven MCP server connections on application startup.
 * Reads entries from {@code jclaw.mcp-servers.*} and creates transport
 * providers that are registered into the {@link McpServerRegistry}.
 */
public class McpServerConfigBootstrap implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfigBootstrap.class);

    private final JClawProperties properties;
    private final McpServerRegistry registry;

    public McpServerConfigBootstrap(JClawProperties properties, McpServerRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        McpServerProperties mcpServers = properties.mcpServers();
        if (mcpServers == null || mcpServers.servers().isEmpty()) {
            return;
        }

        for (Map.Entry<String, McpServerProperties.McpServerEntry> entry : mcpServers.servers().entrySet()) {
            String name = entry.getKey();
            McpServerProperties.McpServerEntry config = entry.getValue();

            if (!config.enabled()) {
                log.debug("Skipping disabled MCP server: {}", name);
                continue;
            }

            try {
                McpToolProvider provider = McpTransportFactory.create(name, config);
                registry.register(provider);
                log.info("Registered config-driven MCP server: {} (type={}, tools={})",
                        name, config.type(), provider.getTools().size());
            } catch (Exception e) {
                log.warn("Failed to connect to MCP server '{}' (type={}): {}",
                        name, config.type(), e.getMessage());
            }
        }
    }
}
