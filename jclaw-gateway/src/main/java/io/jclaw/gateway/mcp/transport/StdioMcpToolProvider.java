package io.jclaw.gateway.mcp.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jclaw.core.mcp.McpToolDefinition;
import io.jclaw.core.mcp.McpToolProvider;
import io.jclaw.core.mcp.McpToolResult;
import io.jclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP tool provider that communicates with a subprocess via JSON-RPC 2.0
 * over stdin/stdout (MCP stdio transport).
 */
public class StdioMcpToolProvider implements McpToolProvider, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpToolProvider.class);

    private final String serverName;
    private final String description;
    private final String command;
    private final List<String> args;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(1);

    private Process process;
    private BufferedWriter stdin;
    private BufferedReader stdout;
    private List<McpToolDefinition> cachedTools;

    public StdioMcpToolProvider(String serverName, String description, String command, List<String> args) {
        this.serverName = serverName;
        this.description = description != null ? description : serverName;
        this.command = command;
        this.args = args != null ? args : List.of();
    }

    /**
     * Start the subprocess and perform the MCP initialize handshake.
     */
    public void start() throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        process = pb.start();

        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        // Initialize handshake
        Map<String, Object> initParams = Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "jclaw", "version", "0.1.0")
        );
        sendRequest("initialize", initParams);

        // Send initialized notification
        sendNotification("notifications/initialized", Map.of());

        // Cache tool list
        refreshTools();

        log.info("Stdio MCP server '{}' started: {} ({} tools)", serverName, command, cachedTools.size());
    }

    private void refreshTools() throws IOException {
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
            log.error("Stdio MCP tool execution failed: {}/{}", serverName, toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    private JsonNode sendRequest(String method, Map<String, Object> params) throws IOException {
        int id = requestId.getAndIncrement();
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", method,
                "params", params
        );

        String json = mapper.writeValueAsString(request);
        stdin.write(json);
        stdin.newLine();
        stdin.flush();

        // Read response line
        String responseLine = stdout.readLine();
        if (responseLine == null) {
            throw new IOException("Stdio MCP process closed unexpectedly");
        }

        JsonNode response = mapper.readTree(responseLine);
        if (response.has("error")) {
            JsonNode error = response.get("error");
            throw new IOException("MCP error: " + error.get("message").asText());
        }

        return response.get("result");
    }

    private void sendNotification(String method, Map<String, Object> params) throws IOException {
        Map<String, Object> notification = Map.of(
                "jsonrpc", "2.0",
                "method", method,
                "params", params
        );
        String json = mapper.writeValueAsString(notification);
        stdin.write(json);
        stdin.newLine();
        stdin.flush();
    }

    @Override
    public void destroy() {
        if (process != null && process.isAlive()) {
            log.info("Shutting down stdio MCP server: {}", serverName);
            process.destroyForcibly();
        }
    }
}
