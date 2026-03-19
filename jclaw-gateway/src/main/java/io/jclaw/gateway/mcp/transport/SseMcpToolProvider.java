package io.jclaw.gateway.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jclaw.core.mcp.McpToolDefinition;
import io.jclaw.core.mcp.McpToolProvider;
import io.jclaw.core.mcp.McpToolResult;
import io.jclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP tool provider that connects via Server-Sent Events (SSE transport).
 * Connects to an SSE endpoint to receive the POST URL, then sends JSON-RPC
 * requests via POST and receives responses via SSE events.
 */
public class SseMcpToolProvider implements McpToolProvider, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SseMcpToolProvider.class);

    private final String serverName;
    private final String description;
    private final String url;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(1);
    private final HttpClient httpClient;

    private volatile String postEndpoint;
    private volatile boolean connected;
    private List<McpToolDefinition> cachedTools;
    private CompletableFuture<Void> sseConnection;

    public SseMcpToolProvider(String serverName, String description, String url) {
        this.serverName = serverName;
        this.description = description != null ? description : serverName;
        this.url = url;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Connect to the SSE endpoint and discover the POST URL.
     */
    public void connect() throws Exception {
        CountDownLatch endpointLatch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        sseConnection = CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "text/event-stream")
                        .GET()
                        .build();

                HttpResponse<java.io.InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8));

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event: endpoint")) {
                        String dataLine = reader.readLine();
                        if (dataLine != null && dataLine.startsWith("data: ")) {
                            postEndpoint = resolveUrl(dataLine.substring(6).trim());
                            connected = true;
                            endpointLatch.countDown();
                        }
                    }
                }
            } catch (Exception e) {
                error.set(e);
                endpointLatch.countDown();
            }
        });

        if (!endpointLatch.await(10, TimeUnit.SECONDS)) {
            throw new Exception("Timeout waiting for SSE endpoint from " + url);
        }

        if (error.get() != null) {
            throw error.get();
        }

        // Initialize and cache tools
        sendInitialize();
        refreshTools();

        log.info("SSE MCP server '{}' connected: {} ({} tools)", serverName, url, cachedTools.size());
    }

    private String resolveUrl(String endpoint) {
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        // Relative URL — resolve against base
        URI base = URI.create(url);
        return base.resolve(endpoint).toString();
    }

    private void sendInitialize() throws Exception {
        Map<String, Object> initParams = Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "jclaw", "version", "0.1.0")
        );
        sendRequest("initialize", initParams);
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
            log.error("SSE MCP tool execution failed: {}/{}", serverName, toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    private JsonNode sendRequest(String method, Map<String, Object> params) throws Exception {
        if (postEndpoint == null) {
            throw new IllegalStateException("SSE MCP server not connected: " + serverName);
        }

        int id = requestId.getAndIncrement();
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", method,
                "params", params
        );

        String json = mapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(postEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("SSE MCP POST failed with status " + response.statusCode());
        }

        JsonNode responseNode = mapper.readTree(response.body());
        if (responseNode.has("error")) {
            JsonNode error = responseNode.get("error");
            throw new Exception("MCP error: " + error.get("message").asText());
        }

        return responseNode.get("result");
    }

    @Override
    public void destroy() {
        connected = false;
        if (sseConnection != null) {
            sseConnection.cancel(true);
        }
        log.info("SSE MCP server disconnected: {}", serverName);
    }
}
