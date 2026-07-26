package io.jaiclaw.gateway.mcp.transport.server;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import io.jaiclaw.core.mcp.*;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.gateway.mcp.McpServerRegistry;
import io.jaiclaw.gateway.tenant.TenantResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Generic SSE server controller that exposes any registered {@link McpToolProvider}
 * and/or {@link McpResourceProvider} via the MCP SSE transport protocol.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /mcp/{serverName}/sse} — SSE connection, sends endpoint URI</li>
 *   <li>{@code POST /mcp/{serverName}/jsonrpc} — JSON-RPC 2.0 request handler</li>
 * </ul>
 */
@RestController
@RequestMapping("/mcp")
public class McpSseServerController {

    private static final Logger log = LoggerFactory.getLogger(McpSseServerController.class);
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final McpServerRegistry registry;
    private final TenantResolver tenantResolver;
    private final ObjectMapper objectMapper;

    public McpSseServerController(McpServerRegistry registry, TenantResolver tenantResolver, ObjectMapper objectMapper) {
        this.registry = registry;
        this.tenantResolver = tenantResolver;
        this.objectMapper = objectMapper;
    }

    public McpSseServerController(McpServerRegistry registry, ObjectMapper objectMapper) {
        this(registry, null, objectMapper);
    }

    /**
     * SSE endpoint — on connect, sends the JSON-RPC endpoint URI to the client.
     */
    @GetMapping(value = "/{serverName}/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sseConnect(@PathVariable String serverName) {
        if (!registry.hasToolProvider(serverName) && !registry.hasResourceProvider(serverName)) {
            throw new IllegalArgumentException("Unknown MCP server: " + serverName);
        }

        SseEmitter emitter = new SseEmitter(0L); // no timeout
        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/mcp/" + serverName + "/jsonrpc"));
            log.debug("SSE client connected to MCP server: {}", serverName);
        } catch (IOException e) {
            log.error("Failed to send SSE endpoint event for server: {}", serverName, e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * JSON-RPC 2.0 endpoint — handles initialize, tools/list, tools/call,
     * resources/list, resources/read, and notifications.
     */
    @PostMapping(value = "/{serverName}/jsonrpc",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> jsonRpc(
            @PathVariable String serverName,
            @RequestBody JsonNode request,
            @RequestHeader Map<String, String> headers) {

        if (!registry.hasToolProvider(serverName) && !registry.hasResourceProvider(serverName)) {
            return ResponseEntity.notFound().build();
        }

        String method = request.has("method") ? request.get("method").asText() : "";
        JsonNode id = request.get("id"); // may be null for notifications
        JsonNode params = request.get("params");

        // Notifications have no id — return 204
        if (id == null || id.isNull()) {
            log.debug("MCP notification received: {}/{}", serverName, method);
            return ResponseEntity.noContent().build();
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);

        try {
            JsonNode result = switch (method) {
                case "initialize" -> handleInitialize(serverName);
                case "tools/list" -> handleToolsList(serverName);
                case "tools/call" -> handleToolsCall(serverName, params, headers);
                case "resources/list" -> handleResourcesList(serverName);
                case "resources/read" -> handleResourcesRead(serverName, params, headers);
                default -> throw new UnsupportedOperationException("Unknown method: " + method);
            };
            response.set("result", result);
        } catch (UnsupportedOperationException e) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("code", -32601);
            error.put("message", e.getMessage());
            response.set("error", error);
        } catch (Exception e) {
            log.error("JSON-RPC error for {}/{}", serverName, method, e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("code", -32603);
            error.put("message", "Internal error: " + e.getMessage());
            response.set("error", error);
        }

        return ResponseEntity.ok(response);
    }

    private JsonNode handleInitialize(String serverName) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);

        ObjectNode capabilities = objectMapper.createObjectNode();
        if (registry.hasToolProvider(serverName)) {
            capabilities.putObject("tools");
        }
        if (registry.hasResourceProvider(serverName)) {
            capabilities.putObject("resources");
        }
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", serverName);
        serverInfo.put("version", "0.1.0");
        result.set("serverInfo", serverInfo);

        return result;
    }

    private JsonNode handleToolsList(String serverName) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = objectMapper.createArrayNode();

        registry.get(serverName).ifPresent(provider -> {
            for (McpToolDefinition tool : provider.getTools()) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());
                try {
                    toolNode.set("inputSchema", objectMapper.readTree(tool.inputSchema()));
                } catch (Exception e) {
                    toolNode.put("inputSchema", tool.inputSchema());
                }
                tools.add(toolNode);
            }
        });

        result.set("tools", tools);
        return result;
    }

    private JsonNode handleToolsCall(String serverName, JsonNode params, Map<String, String> headers) {
        Optional<McpToolProvider> providerOpt = registry.get(serverName);
        if (providerOpt.isEmpty()) {
            throw new UnsupportedOperationException("No tool provider for server: " + serverName);
        }

        McpToolProvider provider = providerOpt.get();
        String toolName = params.has("name") ? params.get("name").asText() : "";
        Map<String, Object> args = Map.of();
        if (params.has("arguments")) {
            try {
                args = objectMapper.convertValue(params.get("arguments"),
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            } catch (Exception e) {
                log.warn("Failed to parse tool arguments", e);
            }
        }

        TenantContext tenant = null;
        if (tenantResolver != null) {
            tenant = tenantResolver.resolve(headers).orElse(null);
        }
        if (tenant != null) {
            TenantContextHolder.set(tenant);
        }

        try {
            McpToolResult toolResult = provider.execute(toolName, args, tenant);

            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode content = objectMapper.createArrayNode();
            ObjectNode textContent = objectMapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", toolResult.content());
            content.add(textContent);
            result.set("content", content);
            result.put("isError", toolResult.isError());

            return result;
        } finally {
            if (tenant != null) {
                TenantContextHolder.clear();
            }
        }
    }

    private JsonNode handleResourcesList(String serverName) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode resources = objectMapper.createArrayNode();

        registry.getResourceProvider(serverName).ifPresent(provider -> {
            for (McpResourceDefinition res : provider.getResources()) {
                ObjectNode resNode = objectMapper.createObjectNode();
                resNode.put("uri", res.uri());
                resNode.put("name", res.name());
                resNode.put("mimeType", res.mimeType());
                if (res.description() != null) {
                    resNode.put("description", res.description());
                }
                resources.add(resNode);
            }
        });

        result.set("resources", resources);
        return result;
    }

    private JsonNode handleResourcesRead(String serverName, JsonNode params, Map<String, String> headers) {
        Optional<McpResourceProvider> providerOpt = registry.getResourceProvider(serverName);
        if (providerOpt.isEmpty()) {
            throw new UnsupportedOperationException("No resource provider for server: " + serverName);
        }

        String uri = params.has("uri") ? params.get("uri").asText() : "";

        TenantContext tenant = null;
        if (tenantResolver != null) {
            tenant = tenantResolver.resolve(headers).orElse(null);
        }
        if (tenant != null) {
            TenantContextHolder.set(tenant);
        }

        try {
            Optional<McpResourceContent> contentOpt = providerOpt.get().read(uri, tenant);
            if (contentOpt.isEmpty()) {
                throw new IllegalArgumentException("Resource not found: " + uri);
            }

            McpResourceContent content = contentOpt.get();
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode contents = objectMapper.createArrayNode();
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("uri", content.uri());
            entry.put("mimeType", content.mimeType());
            entry.put("text", content.text());
            contents.add(entry);
            result.set("contents", contents);
            return result;
        } finally {
            if (tenant != null) {
                TenantContextHolder.clear();
            }
        }
    }
}
