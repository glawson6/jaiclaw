package io.jaiclaw.gateway.mcp;

import io.jaiclaw.core.mcp.McpResourceContent;
import io.jaiclaw.core.mcp.McpResourceDefinition;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.gateway.tenant.TenantResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller exposing hosted MCP servers at {@code /mcp/{serverName}}.
 * Supports tool listing/execution and resource listing/reading.
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
                .map(name -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("name", name);

                    registry.get(name).ifPresent(p -> {
                        info.put("description", p.getServerDescription());
                        info.put("toolCount", p.getTools().size());
                    });

                    registry.getResourceProvider(name).ifPresent(p -> {
                        info.putIfAbsent("description", p.getServerDescription());
                        info.put("resourceCount", p.getResources().size());
                    });

                    return info;
                })
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
            TenantContext tenant = TenantContextHolder.get();
            if (tenant == null && tenantResolver != null) {
                // Only resolve when authentication left no tenant — see
                // SecurityContextTenantResolver for why the order matters.
                tenant = tenantResolver.resolve(headers).orElse(null);
                if (tenant != null) {
                    TenantContextHolder.set(tenant);
                }
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

    /** List resources for a specific MCP server. */
    @GetMapping("/{serverName}/resources")
    public ResponseEntity<Map<String, Object>> listResources(@PathVariable String serverName) {
        return registry.getResourceProvider(serverName)
                .map(provider -> {
                    List<Map<String, String>> resources = provider.getResources().stream()
                            .map(r -> {
                                Map<String, String> m = new LinkedHashMap<>();
                                m.put("uri", r.uri());
                                m.put("name", r.name());
                                m.put("mimeType", r.mimeType());
                                if (r.description() != null) {
                                    m.put("description", r.description());
                                }
                                return m;
                            })
                            .toList();
                    return ResponseEntity.ok(Map.<String, Object>of("resources", resources));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Read a resource by URI on a specific MCP server. */
    @PostMapping("/{serverName}/resources/read")
    public ResponseEntity<Map<String, Object>> readResource(
            @PathVariable String serverName,
            @RequestBody Map<String, String> body,
            @RequestHeader Map<String, String> headers) {

        return registry.getResourceProvider(serverName).map(provider -> {
            String uri = body.get("uri");
            if (uri == null || uri.isBlank()) {
                return ResponseEntity.badRequest().<Map<String, Object>>body(
                        Map.of("error", "Missing 'uri' in request body"));
            }

            TenantContext tenant = TenantContextHolder.get();
            if (tenant == null && tenantResolver != null) {
                // Only resolve when authentication left no tenant — see
                // SecurityContextTenantResolver for why the order matters.
                tenant = tenantResolver.resolve(headers).orElse(null);
                if (tenant != null) {
                    TenantContextHolder.set(tenant);
                }
            }

            try {
                return provider.read(uri, tenant)
                        .map(content -> ResponseEntity.ok(Map.<String, Object>of(
                                "uri", content.uri(),
                                "mimeType", content.mimeType(),
                                "text", content.text())))
                        .orElse(ResponseEntity.notFound().build());
            } finally {
                TenantContextHolder.clear();
            }
        }).orElse(ResponseEntity.notFound().build());
    }
}
