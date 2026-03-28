package io.jaiclaw.config;

/**
 * Proxy configuration for outbound HTTP connections.
 * Bound from {@code jaiclaw.http.proxy.*} in application.yml.
 */
public record HttpProxyProperties(
        String host,
        int port,
        String username,
        String password,
        String nonProxyHosts
) {
    public static final HttpProxyProperties DEFAULT = new HttpProxyProperties(
            null, 0, null, null, null
    );

    public HttpProxyProperties {
        if (nonProxyHosts == null) nonProxyHosts = "";
    }

    /** True if a proxy host is explicitly configured. */
    public boolean isConfigured() {
        return host != null && !host.isBlank() && port > 0;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String host;
        private int port;
        private String username;
        private String password;
        private String nonProxyHosts;

        public Builder host(String host) { this.host = host; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder password(String password) { this.password = password; return this; }
        public Builder nonProxyHosts(String nonProxyHosts) { this.nonProxyHosts = nonProxyHosts; return this; }

        public HttpProxyProperties build() {
            return new HttpProxyProperties(host, port, username, password, nonProxyHosts);
        }
    }
}
