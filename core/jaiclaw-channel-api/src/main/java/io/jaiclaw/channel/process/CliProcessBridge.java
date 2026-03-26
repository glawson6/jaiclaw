package io.jaiclaw.channel.process;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Manages an external CLI process lifecycle and provides JSON-RPC 2.0
 * communication over TCP or stdio. Reusable for any CLI-backed adapter
 * (e.g. signal-cli, matrix-commander, etc.).
 *
 * <p>Features:
 * <ul>
 *   <li>Process start/stop with graceful shutdown (SIGTERM → wait → SIGKILL)</li>
 *   <li>JSON-RPC communication via {@link JsonRpcClient}</li>
 *   <li>Health check via process liveness</li>
 *   <li>Auto-restart on crash (configurable max restarts)</li>
 *   <li>Push notification support via listener callback</li>
 * </ul>
 */
public class CliProcessBridge {

    private static final Logger log = LoggerFactory.getLogger(CliProcessBridge.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final CliProcessConfig config;
    private final JsonRpcClient rpcClient;
    private final AtomicInteger restartCount = new AtomicInteger(0);

    private Process process;
    private Thread notificationReaderThread;
    private Thread healthCheckThread;
    private Consumer<JsonNode> notificationListener;
    private volatile boolean stopping = false;

    public CliProcessBridge(CliProcessConfig config) {
        this(config, new JsonRpcClient());
    }

    public CliProcessBridge(CliProcessConfig config, JsonRpcClient rpcClient) {
        this.config = config;
        this.rpcClient = rpcClient;
    }

    /**
     * Set a listener for JSON-RPC notifications from the process.
     * Must be called before {@link #start()}.
     */
    public void setNotificationListener(Consumer<JsonNode> listener) {
        this.notificationListener = listener;
        rpcClient.setNotificationListener(listener);
    }

    /**
     * Start the CLI process and establish JSON-RPC communication.
     */
    public void start() throws IOException {
        stopping = false;
        var cmd = new ArrayList<String>();
        cmd.add(config.command());
        cmd.addAll(config.args());

        var pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        if (config.workingDir() != null) {
            pb.directory(new File(config.workingDir()));
        }

        log.info("Starting CLI process: {}", String.join(" ", cmd));
        process = pb.start();

        if (config.useTcp()) {
            // Wait for TCP port to become available
            waitForTcpPort(config.tcpPort());
            rpcClient.connectTcp("localhost", config.tcpPort());
        } else {
            rpcClient.connectStdio(process.getInputStream(), process.getOutputStream());
        }

        // Start health check thread if auto-restart is enabled
        if (config.maxRestarts() > 0) {
            startHealthCheck();
        }

        log.info("CLI process started (pid={})", process.pid());
    }

    /**
     * Start a background thread that reads notifications from the JSON-RPC stream.
     * Call this after {@link #start()} for adapters that receive push notifications.
     */
    public void startNotificationReader() {
        notificationReaderThread = rpcClient.startNotificationReader();
    }

    /**
     * Send a JSON-RPC request to the process and return the result.
     */
    public JsonNode sendRequest(String method, Map<String, Object> params) throws IOException {
        return rpcClient.sendRequest(method, params);
    }

    /**
     * Send a JSON-RPC notification to the process (no response expected).
     */
    public void sendNotification(String method, Map<String, Object> params) throws IOException {
        rpcClient.sendNotification(method, params);
    }

    /**
     * Check if the managed process is alive.
     */
    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    /**
     * Gracefully stop the CLI process.
     * Sends SIGTERM, waits up to 5 seconds, then SIGKILL if still running.
     */
    public void stop() {
        stopping = true;

        if (healthCheckThread != null) {
            healthCheckThread.interrupt();
        }
        if (notificationReaderThread != null) {
            notificationReaderThread.interrupt();
        }

        try {
            rpcClient.close();
        } catch (IOException e) {
            log.debug("Error closing JSON-RPC client: {}", e.getMessage());
        }

        if (process != null && process.isAlive()) {
            log.info("Stopping CLI process (pid={})...", process.pid());
            process.destroy(); // SIGTERM
            try {
                if (!process.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("CLI process did not exit gracefully, force-killing");
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            log.info("CLI process stopped");
        }
    }

    /** Number of times the process has been restarted. */
    public int getRestartCount() {
        return restartCount.get();
    }

    // --- Internals ---

    private void waitForTcpPort(int port) throws IOException {
        int maxAttempts = 30; // 30 * 200ms = 6 seconds max
        for (int i = 0; i < maxAttempts; i++) {
            try {
                new java.net.Socket("localhost", port).close();
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for TCP port " + port);
                }
            }
        }
        throw new IOException("TCP port " + port + " not available after " + maxAttempts + " attempts");
    }

    private void startHealthCheck() {
        healthCheckThread = Thread.ofVirtual().name("cli-health-check").start(() -> {
            while (!stopping && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(config.healthCheckIntervalSeconds() * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (!stopping && !isAlive()) {
                    int count = restartCount.incrementAndGet();
                    if (count <= config.maxRestarts()) {
                        log.warn("CLI process died, attempting restart {}/{}", count, config.maxRestarts());
                        try {
                            start();
                        } catch (IOException e) {
                            log.error("Failed to restart CLI process: {}", e.getMessage());
                        }
                    } else {
                        log.error("CLI process died, max restarts ({}) exceeded", config.maxRestarts());
                        break;
                    }
                }
            }
        });
    }
}
