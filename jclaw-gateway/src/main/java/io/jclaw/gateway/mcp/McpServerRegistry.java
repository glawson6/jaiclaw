package io.jclaw.gateway.mcp;

import io.jclaw.core.mcp.McpToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of MCP tool providers keyed by server name.
 * The gateway uses this to route incoming MCP requests to the correct provider.
 */
public class McpServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpServerRegistry.class);

    private final Map<String, McpToolProvider> providers = new ConcurrentHashMap<>();

    public McpServerRegistry() {}

    public McpServerRegistry(List<McpToolProvider> providers) {
        providers.forEach(this::register);
    }

    public void register(McpToolProvider provider) {
        providers.put(provider.getServerName(), provider);
        log.info("Registered MCP server: {} ({} tools)", provider.getServerName(), provider.getTools().size());
    }

    public Optional<McpToolProvider> get(String serverName) {
        return Optional.ofNullable(providers.get(serverName));
    }

    public List<String> serverNames() {
        return List.copyOf(providers.keySet());
    }

    public int size() {
        return providers.size();
    }
}
