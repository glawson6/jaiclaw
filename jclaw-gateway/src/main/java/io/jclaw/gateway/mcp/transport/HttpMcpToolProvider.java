package io.jclaw.gateway.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jclaw.core.mcp.McpToolDefinition;
import io.jclaw.core.mcp.McpToolProvider;
import io.jclaw.core.mcp.McpToolResult;
import io.jclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP tool provider that communicates over streamable HTTP transport.
 * Sends JSON-RPC 2.0 requests as POST to a single endpoint and receives
 * JSON-RPC responses.
 */
public class HttpMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpMcpToolProvider.class);

    private final String serverName;
    private final String description;
    private final String url;
    private final String authToken;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(1);
    private final HttpClient httpClient;

    private List<McpToolDefinition> cachedTools;

    public HttpMcpToolProvider(String serverName, String description, String url, String authToken) {
        this.serverName = serverName;
        this.description = description != null ? description : serverName;
        this.url = url;
        this.authToken = authToken;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Perform the MCP initialize handshake and cache the tool list.
     */
    public void initialize() throws Exception {
        Map<String, Object> initParams = Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "jclaw", "version", "0.1.0")
        );
        sendRequest("initialize", initParams);
        refreshTools();
        log.info("HTTP MCP server '{}' initialized: {} ({} tools)", serverName, url, cachedTools.size());
    }

    private void refreshTools() throws Exception {
        JsonNode result = sendRequest("tools/list", Map.of());
        cachedTools = new ArrayList<>();
        JsonNode tools = result.get("tools");
        if (tools != null && tools.isArray()) {
            for (JsonNode tool : tools) {
                String name = tool.get("name").asText();
                String desc = tool.has("description") ? tool.get("description").asText() : "";
                String schema = tool.has("inputSchema") ? mapper.writeValueAsString(tool.get("inputSchema")) : "{}";
                cachedTools.add(new McpToolDefinition(name, desc, schema));
            }
        }
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public String getServerDescription() {
        return description;
    }

    @Override
    public List<McpToolDefinition> getTools() {
        return cachedTools != null ? cachedTools : List.of();
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        try {
            Map<String, Object> params = Map.of("name", toolName, "arguments", args);
            JsonNode result = sendRequest("tools/call", params);

            if (result.has("content")) {
                JsonNode content = result.get("content");
                StringBuilder sb = new StringBuilder();
                if (content.isArray()) {
                    for (JsonNode item : content) {
                        if (item.has("text")) {
                            sb.append(item.get("text").asText());
                        }
                    }
                }
                boolean isError = result.has("isError") && result.get("isError").asBoolean();
                return isError ? McpToolResult.error(sb.toString()) : McpToolResult.success(sb.toString());
            }
            return McpToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            log.error("HTTP MCP tool execution failed: {}/{}", serverName, toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    private JsonNode sendRequest(String method, Map<String, Object> params) throws Exception {
        int id = requestId.getAndIncrement();
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", method,
                "params", params
        );

        String json = mapper.writeValueAsString(request);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));

        if (authToken != null && !authToken.isBlank()) {
            builder.header("Authorization", "Bearer " + authToken);
        }

        HttpRequest httpRequest = builder.build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("HTTP MCP request failed with status " + response.statusCode());
        }

        JsonNode responseNode = mapper.readTree(response.body());
        if (responseNode.has("error")) {
            JsonNode error = responseNode.get("error");
            throw new Exception("MCP error: " + error.get("message").asText());
        }

        return responseNode.get("result");
    }
}
