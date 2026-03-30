package io.jaiclaw.gateway.mcp.transport.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Reusable server-side stdio bridge for the MCP protocol.
 * Reads JSON-RPC 2.0 requests line-by-line from stdin,
 * dispatches to an {@link McpToolProvider}, and writes responses to stdout.
 *
 * <p>This is the server-side counterpart to {@code StdioMcpToolProvider} (client-side).
 */
public class McpStdioBridge {

    private static final Logger log = LoggerFactory.getLogger(McpStdioBridge.class);
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final McpToolProvider provider;
    private final ObjectMapper objectMapper;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    public McpStdioBridge(McpToolProvider provider, ObjectMapper objectMapper) {
        this(provider, objectMapper, System.in, System.out);
    }

    public McpStdioBridge(McpToolProvider provider, ObjectMapper objectMapper,
                          InputStream inputStream, OutputStream outputStream) {
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    /**
     * Run the stdio bridge. Blocks until EOF on stdin.
     */
    public void run() throws IOException {
        log.info("MCP stdio bridge started for server: {}", provider.getServerName());

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
        capabilities.putObject("tools");
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", provider.getServerName());
        serverInfo.put("version", "0.1.0");
        result.set("serverInfo", serverInfo);

        return result;
    }

    private JsonNode handleToolsList() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = objectMapper.createArrayNode();

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

        result.set("tools", tools);
        return result;
    }

    private JsonNode handleToolsCall(JsonNode params) {
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

        McpToolResult toolResult = provider.execute(toolName, args, null);

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
}
