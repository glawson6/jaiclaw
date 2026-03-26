package io.jaiclaw.channel.signal;

import java.util.Set;

/**
 * Configuration for the Signal channel adapter.
 *
 * <p>Two integration modes:
 * <ul>
 *   <li>{@link SignalMode#EMBEDDED} — gateway manages a signal-cli daemon process,
 *       communicating via JSON-RPC 2.0 over TCP</li>
 *   <li>{@link SignalMode#HTTP_CLIENT} — connects to an external signal-cli-rest-api
 *       sidecar via HTTP polling</li>
 * </ul>
 *
 * @param mode               integration mode
 * @param phoneNumber        the Signal phone number (e.g. "+14155551234")
 * @param enabled            whether the adapter is enabled
 * @param apiUrl             base URL for HTTP_CLIENT mode (e.g. "http://localhost:8080")
 * @param pollIntervalSeconds polling interval for HTTP_CLIENT mode
 * @param cliCommand         signal-cli executable path for EMBEDDED mode
 * @param tcpPort            TCP port for JSON-RPC in EMBEDDED mode
 * @param allowedSenderIds   phone numbers allowed to send messages (empty = allow all)
 */
public record SignalConfig(
        SignalMode mode,
        String phoneNumber,
        boolean enabled,
        String apiUrl,
        int pollIntervalSeconds,
        String cliCommand,
        int tcpPort,
        Set<String> allowedSenderIds
) {
    public SignalConfig {
        if (mode == null) mode = SignalMode.HTTP_CLIENT;
        if (phoneNumber == null) phoneNumber = "";
        if (apiUrl == null) apiUrl = "http://localhost:8080";
        if (pollIntervalSeconds <= 0) pollIntervalSeconds = 2;
        if (cliCommand == null || cliCommand.isBlank()) cliCommand = "signal-cli";
        if (tcpPort <= 0) tcpPort = 7583;
        if (allowedSenderIds == null) allowedSenderIds = Set.of();
    }

    public boolean isSenderAllowed(String senderId) {
        return allowedSenderIds.isEmpty() || allowedSenderIds.contains(senderId);
    }

    public static final SignalConfig DISABLED = new SignalConfig(
            SignalMode.HTTP_CLIENT, "", false,
            "http://localhost:8080", 2, "signal-cli", 7583, Set.of()
    );
}
