package io.jclaw.gateway.mcp;

import io.jclaw.core.mcp.McpToolDefinition;
import io.jclaw.core.mcp.McpToolResult;
import io.jclaw.core.tenant.TenantContext;
import io.jclaw.core.tenant.TenantContextHolder;
import io.jclaw.gateway.tenant.TenantResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing hosted MCP servers at {@code /mcp/{serverName}}.
 * Supports tool listing and tool execution via JSON-RPC-style endpoints.
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final McpServerRegistry registry;
    private final TenantResolver tenantResolver;

    public McpController(McpServerRegistry registry, TenantResolver tenantResolver) {
        this.registry = registry;
        this.tenantResolver = tenantResolver;
    }

    public McpController(McpServerRegistry registry) {
        this(registry, null);
    }

    /** List all available MCP servers. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listServers() {
        var servers = registry.serverNames().stream()
                .map(name -> registry.get(name).map(p -> Map.of(
                        "name", (Object) p.getServerName(),
                        "description", (Object) p.getServerDescription(),
                        "toolCount", (Object) p.getTools().size()
                )).orElse(Map.of()))
                .toList();
        return ResponseEntity.ok(Map.of("servers", servers));
    }

    /** List tools for a specific MCP server. */
    @GetMapping("/{serverName}/tools")
    public ResponseEntity<Map<String, Object>> listTools(@PathVariable String serverName) {
        return registry.get(serverName)
                .map(provider -> {
                    List<Map<String, String>> tools = provider.getTools().stream()
                            .map(t -> Map.of(
                                    "name", t.name(),
                                    "description", t.description(),
                                    "inputSchema", t.inputSchema()))
                            .toList();
                    return ResponseEntity.ok(Map.<String, Object>of("tools", tools));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Execute a tool on a specific MCP server. */
    @PostMapping("/{serverName}/tools/{toolName}")
    public ResponseEntity<Map<String, Object>> executeTool(
            @PathVariable String serverName,
            @PathVariable String toolName,
            @RequestBody(required = false) Map<String, Object> args,
            @RequestHeader Map<String, String> headers) {

        return registry.get(serverName).map(provider -> {
            // Resolve tenant from headers
            TenantContext tenant = null;
            if (tenantResolver != null) {
                tenant = tenantResolver.resolve(headers).orElse(null);
            }
            if (tenant != null) {
                TenantContextHolder.set(tenant);
            }

            try {
                McpToolResult result = provider.execute(toolName, args != null ? args : Map.of(), tenant);
                return ResponseEntity.ok(Map.<String, Object>of(
                        "content", result.content(),
                        "isError", result.isError()));
            } catch (Exception e) {
                log.error("MCP tool execution failed: {}/{}", serverName, toolName, e);
                return ResponseEntity.ok(Map.<String, Object>of(
                        "content", "Tool execution failed: " + e.getMessage(),
                        "isError", true));
            } finally {
                TenantContextHolder.clear();
            }
        }).orElse(ResponseEntity.notFound().build());
    }
}
