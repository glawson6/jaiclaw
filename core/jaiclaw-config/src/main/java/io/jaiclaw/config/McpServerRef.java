package io.jaiclaw.config;

import java.util.List;

/**
 * Per-tenant MCP server reference. Can either reference a global server by name
 * or define a tenant-specific inline server.
 *
 * @param name      server name (references global if url is null)
 * @param url       endpoint URL (null = reference global server by name)
 * @param type      transport type: "stdio", "sse", or "http"
 * @param command   command to execute (stdio only)
 * @param args      command arguments (stdio only)
 * @param authToken Bearer token for authentication
 * @param enabled   whether this server is active
 * @param tools     restrict to specific tools from this server (empty = all)
 */
public record McpServerRef(
        String name,
        String url,
        String type,
        String command,
        List<String> args,
        String authToken,
        boolean enabled,
        List<String> tools
) {
    public McpServerRef {
        if (args == null) args = List.of();
        if (tools == null) tools = List.of();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private String url;
        private String type;
        private String command;
        private List<String> args;
        private String authToken;
        private boolean enabled;
        private List<String> tools;

        public Builder name(String name) { this.name = name; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder command(String command) { this.command = command; return this; }
        public Builder args(List<String> args) { this.args = args; return this; }
        public Builder authToken(String authToken) { this.authToken = authToken; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder tools(List<String> tools) { this.tools = tools; return this; }

        public McpServerRef build() {
            return new McpServerRef(name, url, type, command, args, authToken, enabled, tools);
        }
    }
}
