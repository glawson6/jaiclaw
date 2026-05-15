package io.jaiclaw.gateway.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP tool provider that connects via Server-Sent Events (SSE transport).
 * Connects to an SSE endpoint to receive the POST URL, then sends JSON-RPC
 * requests via POST. Responses arrive asynchronously on the SSE stream as
 * {@code event:message} events and are correlated by JSON-RPC request ID.
 *
 * <p>HTTP operations are delegated to an {@link SseHttpTransport} instance,
 * allowing the transport layer to be swapped (e.g. {@code HttpClient} vs
 * {@code WebClient}) without changing the SSE protocol logic.</p>
 */
public class SseMcpToolProvider implements McpToolProvider, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SseMcpToolProvider.class);
    private static final int REQUEST_TIMEOUT_SECONDS = 30;

    private final String serverName;
    private final String description;
    private final String url;
    private final SseHttpTransport transport;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();

    private volatile String postEndpoint;
    private volatile boolean connected;
    private List<McpToolDefinition> cachedTools;
    private Thread sseReaderThread;

    public SseMcpToolProvider(String serverName, String description, String url, SseHttpTransport transport) {
        this.serverName = serverName;
        this.description = description != null ? description : serverName;
        this.url = url;
        this.transport = transport;
    }

    /**
     * Connect to the SSE endpoint and discover the POST URL.
     */
    public void connect() throws Exception {
        CountDownLatch endpointLatch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        sseReaderThread = new Thread(() -> {
            try {
                log.debug("[{}] SSE reader: calling connectSseStream", serverName);
                InputStream stream = transport.connectSseStream(url);
                log.debug("[{}] SSE reader: connectSseStream returned, creating BufferedReader", serverName);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));

                String currentEvent = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[{}] SSE reader: line='{}' currentEvent={}", serverName, line, currentEvent);
                    if (line.startsWith("event:")) {
                        currentEvent = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if ("endpoint".equals(currentEvent)) {
                            postEndpoint = resolveUrl(data);
                            connected = true;
                            log.debug("[{}] SSE reader: endpoint discovered: {}", serverName, postEndpoint);
                            endpointLatch.countDown();
                        } else if ("message".equals(currentEvent)) {
                            handleSseMessage(data);
                        }
                        currentEvent = null;
                    } else if (line.isEmpty()) {
                        currentEvent = null;
                    }
                }
                log.debug("[{}] SSE reader: stream ended (readLine returned null)", serverName);
            } catch (Exception e) {
                if (connected) {
                    log.warn("SSE stream for '{}' closed: {}", serverName, e.getMessage());
                } else {
                    log.error("[{}] SSE reader: error before connected", serverName, e);
                    error.set(e);
                    endpointLatch.countDown();
                }
            }
        }, "mcp-sse-reader-" + serverName);
        sseReaderThread.setDaemon(true);
        sseReaderThread.start();

        if (!endpointLatch.await(10, TimeUnit.SECONDS)) {
            throw new Exception("Timeout waiting for SSE endpoint from " + url);
        }

        if (error.get() != null) {
            throw error.get();
        }

        // Initialize and cache tools
        log.debug("[{}] Sending initialize request", serverName);
        sendInitialize();
        sendNotification("notifications/initialized", Map.of());
        log.debug("[{}] Initialize complete, refreshing tools", serverName);
        refreshTools();
        log.debug("[{}] Tools refreshed", serverName);

        log.info("SSE MCP server '{}' connected: {} ({} tools)", serverName, url, cachedTools.size());
    }

    private void handleSseMessage(String data) {
        try {
            JsonNode responseNode = mapper.readTree(data);
            if (responseNode.has("id") && !responseNode.get("id").isNull()) {
                int id = responseNode.get("id").asInt();
                CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                if (future != null) {
                    future.complete(responseNode);
                } else {
                    log.debug("Received SSE response for unknown request id: {}", id);
                }
            } else {
                // Notification (no id) — log and ignore
                log.debug("Received SSE notification: {}", data);
            }
        } catch (Exception e) {
            log.warn("Failed to parse SSE message: {}", data, e);
        }
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
                "clientInfo", Map.of("name", "jaiclaw", "version", "0.3.0")
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

        // Register a future to receive the response from the SSE stream
        CompletableFuture<JsonNode> responseFuture = new CompletableFuture<>();
        pendingRequests.put(id, responseFuture);

        try {
            String json = mapper.writeValueAsString(request);

            // Fire the POST — response comes via SSE stream, not HTTP body
            log.debug("[{}] POSTing {} (id={}) to {}", serverName, method, id, postEndpoint);
            int statusCode = transport.postJsonRpc(postEndpoint, json);
            log.debug("[{}] POST {} returned status {}", serverName, method, statusCode);
            if (statusCode != 200 && statusCode != 202) {
                pendingRequests.remove(id);
                throw new Exception("SSE MCP POST failed with status " + statusCode);
            }

            // Wait for the response on the SSE stream
            JsonNode responseNode = responseFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (responseNode.has("error")) {
                JsonNode errorNode = responseNode.get("error");
                throw new Exception("MCP error: " + errorNode.get("message").asText());
            }

            return responseNode.get("result");
        } catch (Exception e) {
            pendingRequests.remove(id);
            throw e;
        }
    }

    private void sendNotification(String method, Map<String, Object> params) throws Exception {
        if (postEndpoint == null) {
            throw new IllegalStateException("SSE MCP server not connected: " + serverName);
        }
        Map<String, Object> notification = Map.of(
                "jsonrpc", "2.0",
                "method", method,
                "params", params
        );
        String json = mapper.writeValueAsString(notification);
        log.debug("[{}] Sending notification: {}", serverName, method);
        int statusCode = transport.postJsonRpc(postEndpoint, json);
        if (statusCode != 200 && statusCode != 202 && statusCode != 204) {
            log.warn("[{}] Notification '{}' returned unexpected status {}", serverName, method, statusCode);
        }
    }

    @Override
    public void destroy() {
        connected = false;
        if (sseReaderThread != null) {
            sseReaderThread.interrupt();
        }
        try {
            transport.close();
        } catch (Exception e) {
            log.debug("Error closing SSE transport for '{}': {}", serverName, e.getMessage());
        }
        // Complete any pending requests with an error
        pendingRequests.forEach((id, future) ->
                future.completeExceptionally(new Exception("SSE connection closed")));
        pendingRequests.clear();
        log.info("SSE MCP server disconnected: {}", serverName);
    }
}
