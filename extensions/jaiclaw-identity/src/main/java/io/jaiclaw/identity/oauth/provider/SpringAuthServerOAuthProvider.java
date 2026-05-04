package io.jaiclaw.identity.oauth.provider;

import io.jaiclaw.identity.oauth.OAuthFlowType;
import io.jaiclaw.identity.oauth.OAuthProviderConfig;

import java.util.List;

/**
 * Spring Authorization Server OAuth provider configuration.
 * Auto-derives endpoints from server URL.
 * Supports all three flow types: AUTHORIZATION_CODE, DEVICE_CODE, RESOURCE_OWNER_PASSWORD.
 */
public final class SpringAuthServerOAuthProvider {
    private SpringAuthServerOAuthProvider() {}

    /**
     * Create config from environment variables with default AUTHORIZATION_CODE flow.
     */
    public static OAuthProviderConfig config() {
        return config(OAuthFlowType.AUTHORIZATION_CODE);
    }

    /**
     * Create config from environment variables with specified flow type.
     */
    public static OAuthProviderConfig config(OAuthFlowType flowType) {
        String serverUrl = envOrDefault("SPRING_AUTH_SERVER_URL", "http://localhost:8082");
        String clientId = envOrNull("SPRING_AUTH_SERVER_CLIENT_ID");
        String clientSecret = envOrNull("SPRING_AUTH_SERVER_CLIENT_SECRET");
        String scopes = envOrDefault("SPRING_AUTH_SERVER_SCOPE", "openid profile email");
        return config(serverUrl, clientId, clientSecret, scopes, flowType);
    }

    /**
     * Create config with explicit parameters.
     */
    public static OAuthProviderConfig config(String serverUrl, String clientId,
                                              String clientSecret, String scopes,
                                              OAuthFlowType flowType) {
        return new OAuthProviderConfig(
                "spring-auth-server",
                serverUrl + "/oauth2/authorize",
                serverUrl + "/oauth2/token",
                serverUrl + "/userinfo",
                serverUrl + "/oauth2/device_authorization",
                clientId,
                clientSecret,
                envOrNull("SPRING_AUTH_SERVER_REDIRECT_URI"),
                1458,
                "/oauth-callback",
                List.of(scopes.split("\\s+")),
                flowType
        );
    }

    private static String envOrNull(String name) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value : null;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
