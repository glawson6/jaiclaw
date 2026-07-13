package io.jaiclaw.gateway.mcp.transport.server;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import io.jaiclaw.core.mcp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Reusable server-side stdio bridge for the MCP protocol.
 * Reads JSON-RPC 2.0 requests line-by-line from stdin,
 * dispatches to an {@link McpToolProvider} and/or {@link McpResourceProvider},
 * and writes responses to stdout.
 *
 * <p>This is the server-side counterpart to {@code StdioMcpToolProvider} (client-side).
 */
public class McpStdioBridge {

    private static final Logger log = LoggerFactory.getLogger(McpStdioBridge.class);
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final McpToolProvider toolProvider;
    private final McpResourceProvider resourceProvider;
    private final ObjectMapper objectMapper;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    /** Backward-compatible constructor — tools only. */
    public McpStdioBridge(McpToolProvider provider, ObjectMapper objectMapper) {
        this(provider, null, objectMapper, System.in, System.out);
    }

    public McpStdioBridge(McpToolProvider provider, ObjectMapper objectMapper,
                          InputStream inputStream, OutputStream outputStream) {
        this(provider, null, objectMapper, inputStream, outputStream);
    }

    /** Full constructor — tools and/or resources. */
    public McpStdioBridge(McpToolProvider toolProvider, McpResourceProvider resourceProvider,
                          ObjectMapper objectMapper) {
        this(toolProvider, resourceProvider, objectMapper, System.in, System.out);
    }

    public McpStdioBridge(McpToolProvider toolProvider, McpResourceProvider resourceProvider,
                          ObjectMapper objectMapper, InputStream inputStream, OutputStream outputStream) {
        this.toolProvider = toolProvider;
        this.resourceProvider = resourceProvider;
        this.objectMapper = objectMapper;
        this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    /**
     * Run the stdio bridge. Blocks until EOF on stdin.
     */
    public void run() throws IOException {
        String serverName = toolProvider != null ? toolProvider.getServerName()
                : resourceProvider != null ? resourceProvider.getServerName() : "unknown";
        log.info("MCP stdio bridge started for server: {}", serverName);

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;

            try {
                JsonNode request = objectMapper.readTree(line);
                String method = request.has("method") ? request.get("method").asText() : "";
                JsonNode id = request.get("id");
                JsonNode params = request.has("params") ? request.get("params") : objectMapper.createObjectNode();

                // Notifications (no id) — just acknowledge silently
                if (id == null || id.isNull()) {
                    log.debug("Stdio notification: {}", method);
                    continue;
                }

                ObjectNode response = objectMapper.createObjectNode();
                response.put("jsonrpc", "2.0");
                response.set("id", id);

                try {
                    JsonNode result = switch (method) {
                        case "initialize" -> handleInitialize();
                        case "tools/list" -> handleToolsList();
                        case "tools/call" -> handleToolsCall(params);
                        case "resources/list" -> handleResourcesList();
                        case "resources/read" -> handleResourcesRead(params);
                        default -> throw new UnsupportedOperationException("Unknown method: " + method);
                    };
                    response.set("result", result);
                } catch (UnsupportedOperationException e) {
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("code", -32601);
                    error.put("message", e.getMessage());
                    response.set("error", error);
                } catch (Exception e) {
                    log.error("Stdio bridge error for method: {}", method, e);
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("code", -32603);
                    error.put("message", "Internal error: " + e.getMessage());
                    response.set("error", error);
                }

                writer.write(objectMapper.writeValueAsString(response));
                writer.newLine();
                writer.flush();
            } catch (Exception e) {
                log.error("Failed to process stdio request", e);
            }
        }

        log.info("MCP stdio bridge stopped (EOF)");
    }

    private JsonNode handleInitialize() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);

        ObjectNode capabilities = objectMapper.createObjectNode();
        if (toolProvider != null) {
            capabilities.putObject("tools");
        }
        if (resourceProvider != null) {
            capabilities.putObject("resources");
        }
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = objectMapper.createObjectNode();
        String name = toolProvider != null ? toolProvider.getServerName()
                : resourceProvider.getServerName();
        serverInfo.put("name", name);
        serverInfo.put("version", "0.1.0");
        result.set("serverInfo", serverInfo);

        return result;
    }

    private JsonNode handleToolsList() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = objectMapper.createArrayNode();

        if (toolProvider != null) {
            for (McpToolDefinition tool : toolProvider.getTools()) {
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
        }

        result.set("tools", tools);
        return result;
    }

    private JsonNode handleToolsCall(JsonNode params) {
        if (toolProvider == null) {
            throw new UnsupportedOperationException("No tool provider configured");
        }

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

        McpToolResult toolResult = toolProvider.execute(toolName, args, null);

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", toolResult.content());
        content.add(textContent);
        result.set("content", content);
        result.put("isError", toolResult.isError());

        return result;
    }

    private JsonNode handleResourcesList() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode resources = objectMapper.createArrayNode();

        if (resourceProvider != null) {
            for (McpResourceDefinition res : resourceProvider.getResources()) {
                ObjectNode resNode = objectMapper.createObjectNode();
                resNode.put("uri", res.uri());
                resNode.put("name", res.name());
                resNode.put("mimeType", res.mimeType());
                if (res.description() != null) {
                    resNode.put("description", res.description());
                }
                resources.add(resNode);
            }
        }

        result.set("resources", resources);
        return result;
    }

    private JsonNode handleResourcesRead(JsonNode params) {
        if (resourceProvider == null) {
            throw new UnsupportedOperationException("No resource provider configured");
        }

        String uri = params.has("uri") ? params.get("uri").asText() : "";
        Optional<McpResourceContent> contentOpt = resourceProvider.read(uri, null);

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
    }
}
