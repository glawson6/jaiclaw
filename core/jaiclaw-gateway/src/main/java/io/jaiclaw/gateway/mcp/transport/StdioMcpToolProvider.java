package io.jaiclaw.gateway.mcp.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP tool provider that communicates with a subprocess via JSON-RPC 2.0
 * over stdin/stdout (MCP stdio transport).
 */
public class StdioMcpToolProvider implements McpToolProvider, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpToolProvider.class);

    /** Default idle-timeout after which we recycle the subprocess. Zero disables idle recycling. */
    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(10);

    /** How long we wait after SIGTERM before escalating to SIGKILL during shutdown. */
    private static final Duration SIGTERM_GRACE = Duration.ofSeconds(5);

    private final String serverName;
    private final String description;
    private final String command;
    private final List<String> args;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(1);
    private final Duration idleTimeout;

    private Process process;
    private BufferedWriter stdin;
    private BufferedReader stdout;
    private List<McpToolDefinition> cachedTools;

    // Watchdog state — set on start(), read from the scheduler thread.
    private final AtomicReference<Instant> lastActivityAt = new AtomicReference<>(Instant.EPOCH);
    private ScheduledExecutorService watchdog;
    private Thread shutdownHook;

    /** Shell metacharacters that indicate potential command injection. */
    private static final java.util.regex.Pattern SHELL_METACHAR_PATTERN = java.util.regex.Pattern.compile(
            "[;|&`<>$]"
    );

    public StdioMcpToolProvider(String serverName, String description, String command, List<String> args) {
        this(serverName, description, command, args, DEFAULT_IDLE_TIMEOUT);
    }

    /**
     * @param idleTimeout after N units of no activity, the subprocess is
     *                    recycled on the next call. Zero or negative disables
     *                    idle recycling (subprocess stays alive for the JVM's
     *                    lifetime).
     */
    public StdioMcpToolProvider(String serverName, String description, String command,
                                List<String> args, Duration idleTimeout) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("MCP server command must not be blank for: " + serverName);
        }
        if (SHELL_METACHAR_PATTERN.matcher(command).find()) {
            throw new IllegalArgumentException(
                    "MCP server command contains shell metacharacters: " + command);
        }
        this.serverName = serverName;
        this.description = description != null ? description : serverName;
        this.command = command;
        this.args = args != null ? args : List.of();
        this.idleTimeout = idleTimeout != null ? idleTimeout : DEFAULT_IDLE_TIMEOUT;
    }

    /**
     * Start the subprocess, perform the MCP initialize handshake, and wire the
     * watchdog + JVM shutdown hook. Idempotent — if a live subprocess is
     * already attached, this is a no-op.
     */
    public synchronized void start() throws IOException {
        if (process != null && process.isAlive()) return;

        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);

        // Sanitize environment — only keep safe variables
        Map<String, String> currentEnv = System.getenv();
        pb.environment().clear();
        for (String key : List.of("PATH", "HOME", "LANG", "LC_ALL", "LC_CTYPE",
                "TERM", "USER", "LOGNAME", "SHELL", "TMPDIR", "TZ", "HOSTNAME")) {
            String value = currentEnv.get(key);
            if (value != null) {
                pb.environment().put(key, value);
            }
        }

        process = pb.start();

        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        lastActivityAt.set(Instant.now());

        // Initialize handshake
        Map<String, Object> initParams = Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "jaiclaw", "version", "0.1.0")
        );
        sendRequest("initialize", initParams);

        // Send initialized notification
        sendNotification("notifications/initialized", Map.of());

        // Cache tool list
        refreshTools();

        installWatchdog();
        installShutdownHook();

        log.info("Stdio MCP server '{}' started: {} ({} tools; idle-timeout={})",
                serverName, command, cachedTools.size(), idleTimeout);
    }

    /**
     * If idleTimeout is positive, spin up a single-thread scheduler that
     * every {@code idleTimeout / 4} checks whether we've been idle past the
     * threshold and, if so, terminates the subprocess. The next
     * {@link #execute} call will {@link #start} a fresh one lazily.
     */
    private void installWatchdog() {
        if (idleTimeout == null || idleTimeout.isZero() || idleTimeout.isNegative()) {
            return;
        }
        long checkIntervalMs = Math.max(1000L, idleTimeout.toMillis() / 4);
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-stdio-watchdog-" + serverName);
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleAtFixedRate(this::checkIdle,
                checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
    }

    /** Watchdog tick — kills the subprocess if idle too long. */
    private synchronized void checkIdle() {
        try {
            if (process == null || !process.isAlive()) return;
            Instant last = lastActivityAt.get();
            if (last == null) return;
            Duration idle = Duration.between(last, Instant.now());
            if (idle.compareTo(idleTimeout) > 0) {
                log.info("MCP server '{}' idle for {} (>{} threshold) — recycling subprocess",
                        serverName, idle, idleTimeout);
                terminate(process, "idle-timeout");
                process = null;   // start() will re-spawn on next execute()
            }
        } catch (RuntimeException e) {
            log.warn("Watchdog tick failed for '{}': {}", serverName, e.getMessage());
        }
    }

    /**
     * Install a JVM Runtime shutdown hook so that even if the Spring context
     * doesn't cleanly dispose us (JVM crash midway through app shutdown, kill
     * -9 on the parent, etc.) the subprocess is reaped rather than orphaned.
     * Idempotent — repeated calls skip if the hook is already installed.
     */
    private void installShutdownHook() {
        if (shutdownHook != null) return;
        shutdownHook = new Thread(() -> {
            try {
                if (process != null && process.isAlive()) {
                    log.info("JVM shutdown hook reaping MCP subprocess: {}", serverName);
                    terminate(process, "jvm-shutdown");
                }
            } catch (RuntimeException ignore) {
                // Shutdown hooks must not throw.
            }
        }, "mcp-stdio-shutdown-" + serverName);
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException alreadyShuttingDown) {
            // Shouldn't happen mid-start, but log rather than crash.
            log.warn("Could not install shutdown hook for '{}': JVM already shutting down", serverName);
        }
    }

    /**
     * SIGTERM the subprocess, wait up to {@link #SIGTERM_GRACE} for it to
     * die cleanly, escalate to SIGKILL (destroyForcibly) if it doesn't. Safe
     * to call multiple times. Java's {@link Process#destroy} is SIGTERM on
     * POSIX and TerminateProcess on Windows, so this pattern is cross-platform.
     */
    private void terminate(Process p, String reason) {
        if (p == null || !p.isAlive()) return;
        try {
            p.destroy();
            boolean died = p.waitFor(SIGTERM_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            if (!died) {
                log.warn("MCP subprocess '{}' did not exit within {}s of SIGTERM ({}), escalating to SIGKILL",
                        serverName, SIGTERM_GRACE.toSeconds(), reason);
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
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
            ensureAlive();
            lastActivityAt.set(Instant.now());
            Map<String, Object> params = Map.of("name", toolName, "arguments", args);
            JsonNode result = sendRequest("tools/call", params);
            lastActivityAt.set(Instant.now());

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

    /**
     * Ensure the subprocess is alive; re-{@link #start} if it died (idle
     * recycling reaped it, external kill, crash, etc.).
     */
    private synchronized void ensureAlive() throws IOException {
        if (process == null || !process.isAlive()) {
            log.info("MCP server '{}' not running — starting on demand", serverName);
            start();
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
    public synchronized void destroy() {
        log.info("Shutting down stdio MCP server: {}", serverName);
        if (watchdog != null) {
            watchdog.shutdownNow();
            watchdog = null;
        }
        if (process != null && process.isAlive()) {
            terminate(process, "spring-dispose");
        }
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException alreadyShuttingDown) {
                // Ignore — the JVM is already going down, our hook will run anyway.
            }
            shutdownHook = null;
        }
        process = null;
    }
}
