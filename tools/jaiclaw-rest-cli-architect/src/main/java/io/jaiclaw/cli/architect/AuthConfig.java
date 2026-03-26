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
}
