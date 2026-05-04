package io.jaiclaw.identity.oauth.provider;

import io.jaiclaw.identity.oauth.OAuthFlowType;
import io.jaiclaw.identity.oauth.OAuthProviderConfig;

import java.util.List;

/**
 * Keycloak OAuth provider configuration.
 * Auto-derives OIDC endpoints from server URL + realm.
 * Supports all three flow types: AUTHORIZATION_CODE, DEVICE_CODE, RESOURCE_OWNER_PASSWORD.
 */
public final class KeycloakOAuthProvider {
    private KeycloakOAuthProvider() {}

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
        String serverUrl = envOrDefault("KEYCLOAK_SERVER_URL", "http://localhost:8080");
        String realm = envOrDefault("KEYCLOAK_REALM", "taptech");
        String clientId = envOrNull("KEYCLOAK_CLIENT_ID");
        String clientSecret = envOrNull("KEYCLOAK_CLIENT_SECRET");
        String scopes = envOrDefault("KEYCLOAK_SCOPE", "openid profile email");
        return config(serverUrl, realm, clientId, clientSecret, scopes, flowType);
    }

    /**
     * Create config with explicit parameters.
     */
    public static OAuthProviderConfig config(String serverUrl, String realm,
                                              String clientId, String clientSecret,
                                              String scopes, OAuthFlowType flowType) {
        String realmUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect";

        return new OAuthProviderConfig(
                "keycloak",
                realmUrl + "/auth",
                realmUrl + "/token",
                realmUrl + "/userinfo",
                realmUrl + "/auth/device",
                clientId,
                clientSecret,
                envOrNull("KEYCLOAK_REDIRECT_URI"),
                1457,
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
