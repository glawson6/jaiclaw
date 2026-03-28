package io.jaiclaw.tools.security;

import java.util.List;

/**
 * Configuration properties for the security handshake tools.
 *
 * @param mode              handshake mode (LOCAL, HTTP_CLIENT, ORCHESTRATED)
 * @param mcpServerUrl      MCP server URL for HTTP_CLIENT mode (e.g. "http://localhost:8080/mcp/security")
 * @param mcpServerName     MCP server name for ORCHESTRATED mode
 * @param bootstrap         bootstrap trust level (API_KEY, CLIENT_CERT, MUTUAL)
 * @param apiKey            pre-shared API key for API_KEY/MUTUAL bootstrap
 * @param allowedClientKeys Base64url-encoded public keys for CLIENT_CERT/MUTUAL bootstrap
 * @param server            server-side configuration
 */
public record SecurityHandshakeProperties(
        HandshakeMode mode,
        String mcpServerUrl,
        String mcpServerName,
        BootstrapTrust bootstrap,
        String apiKey,
        List<String> allowedClientKeys,
        ServerProperties server
) {
    public static final SecurityHandshakeProperties DEFAULT =
            new SecurityHandshakeProperties(HandshakeMode.LOCAL, null, null,
                    null, null, null, null);

    public SecurityHandshakeProperties {
        if (mode == null) mode = HandshakeMode.LOCAL;
        if (allowedClientKeys == null) allowedClientKeys = List.of();
        if (server == null) server = ServerProperties.DEFAULT;
    }

    /**
     * Server-side handshake configuration.
     *
     * @param enabled        whether the server-side MCP provider and token filter are active
     * @param mcpServerName  MCP server name (default: "security")
     * @param tokenTtlSeconds session token TTL in seconds (default: 3600)
     */
    public record ServerProperties(
            boolean enabled,
            String mcpServerName,
            int tokenTtlSeconds
    ) {
        public static final ServerProperties DEFAULT = new ServerProperties(false, "security", 3600);

        public ServerProperties {
            if (mcpServerName == null || mcpServerName.isBlank()) mcpServerName = "security";
            if (tokenTtlSeconds <= 0) tokenTtlSeconds = 3600;
        }
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private HandshakeMode mode;
        private String mcpServerUrl;
        private String mcpServerName;
        private BootstrapTrust bootstrap;
        private String apiKey;
        private List<String> allowedClientKeys;
        private ServerProperties server;

        public Builder mode(HandshakeMode mode) { this.mode = mode; return this; }
        public Builder mcpServerUrl(String mcpServerUrl) { this.mcpServerUrl = mcpServerUrl; return this; }
        public Builder mcpServerName(String mcpServerName) { this.mcpServerName = mcpServerName; return this; }
        public Builder bootstrap(BootstrapTrust bootstrap) { this.bootstrap = bootstrap; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder allowedClientKeys(List<String> allowedClientKeys) { this.allowedClientKeys = allowedClientKeys; return this; }
        public Builder server(ServerProperties server) { this.server = server; return this; }

        public SecurityHandshakeProperties build() {
            return new SecurityHandshakeProperties(
                    mode, mcpServerUrl, mcpServerName, bootstrap, apiKey, allowedClientKeys, server);
        }
    }
}
