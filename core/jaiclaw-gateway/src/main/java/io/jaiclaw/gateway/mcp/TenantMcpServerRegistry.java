package io.jaiclaw.gateway.mcp;

import io.jaiclaw.config.McpServerRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-tenant MCP server references. Global MCP servers referenced by name
 * delegate to the existing {@link McpServerRegistry}. Tenant-specific inline servers
 * are managed independently per tenant.
 */
public class TenantMcpServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantMcpServerRegistry.class);

    private final McpServerRegistry globalRegistry;
    private final ConcurrentHashMap<String, List<McpServerRef>> tenantServers = new ConcurrentHashMap<>();

    public TenantMcpServerRegistry(McpServerRegistry globalRegistry) {
        this.globalRegistry = globalRegistry;
    }

    /**
     * Register MCP server references for a tenant.
     */
    public void register(String tenantId, List<McpServerRef> servers) {
        if (servers != null && !servers.isEmpty()) {
            tenantServers.put(tenantId, List.copyOf(servers));
            log.debug("Registered {} MCP servers for tenant: {}", servers.size(), tenantId);
        }
    }

    /**
     * Get all MCP server references for a tenant.
     */
    public List<McpServerRef> getServers(String tenantId) {
        return tenantServers.getOrDefault(tenantId, List.of());
    }

    /**
     * Remove all MCP server references for a tenant.
     */
    public void unregister(String tenantId) {
        tenantServers.remove(tenantId);
    }

    /**
     * Get the global MCP server registry (for resolving servers by name).
     */
    public McpServerRegistry globalRegistry() {
        return globalRegistry;
    }
}
