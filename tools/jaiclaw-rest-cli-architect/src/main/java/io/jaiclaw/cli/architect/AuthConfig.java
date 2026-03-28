package io.jaiclaw.cli.architect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Authentication configuration for the target API.
 *
 * @param type           Auth type: "none", "header", "basic", "oauth2"
 * @param headerName     Header name for header auth (e.g. "Authorization", "X-API-Key")
 * @param headerValuePrefix Prefix before the token value (e.g. "Bearer ", "")
 * @param envVar         Environment variable holding the secret for header auth
 * @param usernameEnv    Env var for basic auth username
 * @param passwordEnv    Env var for basic auth password
 * @param tokenUrl       OAuth2 token endpoint URL
 * @param clientIdEnv    Env var for OAuth2 client ID
 * @param clientSecretEnv Env var for OAuth2 client secret
 * @param scopes         OAuth2 scopes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthConfig(
        String type,
        String headerName,
        String headerValuePrefix,
        String envVar,
        String usernameEnv,
        String passwordEnv,
        String tokenUrl,
        String clientIdEnv,
        String clientSecretEnv,
        List<String> scopes
) {
    public static final AuthConfig NONE = new AuthConfig("none", null, null, null, null, null, null, null, null, List.of());

    public AuthConfig {
        if (type == null) type = "none";
        if (scopes == null) scopes = List.of();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String type;
        private String headerName;
        private String headerValuePrefix;
        private String envVar;
        private String usernameEnv;
        private String passwordEnv;
        private String tokenUrl;
        private String clientIdEnv;
        private String clientSecretEnv;
        private List<String> scopes;

        public Builder type(String type) { this.type = type; return this; }
        public Builder headerName(String headerName) { this.headerName = headerName; return this; }
        public Builder headerValuePrefix(String headerValuePrefix) { this.headerValuePrefix = headerValuePrefix; return this; }
        public Builder envVar(String envVar) { this.envVar = envVar; return this; }
        public Builder usernameEnv(String usernameEnv) { this.usernameEnv = usernameEnv; return this; }
        public Builder passwordEnv(String passwordEnv) { this.passwordEnv = passwordEnv; return this; }
        public Builder tokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; return this; }
        public Builder clientIdEnv(String clientIdEnv) { this.clientIdEnv = clientIdEnv; return this; }
        public Builder clientSecretEnv(String clientSecretEnv) { this.clientSecretEnv = clientSecretEnv; return this; }
        public Builder scopes(List<String> scopes) { this.scopes = scopes; return this; }

        public AuthConfig build() {
            return new AuthConfig(
                    type, headerName, headerValuePrefix, envVar, usernameEnv, passwordEnv, tokenUrl, clientIdEnv, clientSecretEnv, scopes);
        }
    }
}
