package io.jaiclaw.channel.process;

import java.util.List;

/**
 * Configuration for a CLI process managed by {@link CliProcessBridge}.
 * Generic enough for any CLI-backed channel adapter or integration.
 *
 * @param command         the executable command (e.g. "signal-cli")
 * @param args            command-line arguments
 * @param workingDir      working directory for the process (null = inherit)
 * @param tcpPort         TCP port for JSON-RPC communication (0 = use stdio)
 * @param healthCheckIntervalSeconds interval between health checks
 * @param maxRestarts     maximum auto-restarts on crash (0 = no auto-restart)
 */
public record CliProcessConfig(
        String command,
        List<String> args,
        String workingDir,
        int tcpPort,
        int healthCheckIntervalSeconds,
        int maxRestarts
) {
    public CliProcessConfig {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }
        if (args == null) args = List.of();
        if (tcpPort < 0) tcpPort = 0;
        if (healthCheckIntervalSeconds <= 0) healthCheckIntervalSeconds = 30;
        if (maxRestarts < 0) maxRestarts = 0;
    }

    /** Whether to communicate via TCP socket (true) or stdio (false). */
    public boolean useTcp() {
        return tcpPort > 0;
    }
}
