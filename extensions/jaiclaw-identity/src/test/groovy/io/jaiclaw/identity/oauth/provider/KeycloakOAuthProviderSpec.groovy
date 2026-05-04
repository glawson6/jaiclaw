package io.jaiclaw.identity.oauth.provider

import io.jaiclaw.identity.oauth.OAuthFlowType
import io.jaiclaw.identity.oauth.OAuthProviderConfig
import spock.lang.Specification

class KeycloakOAuthProviderSpec extends Specification {

    def "config with explicit parameters derives correct endpoints"() {
        when:
        OAuthProviderConfig config = KeycloakOAuthProvider.config(
                "https://keycloak.example.com", "my-realm",
                "my-client", "my-secret", "openid profile",
                OAuthFlowType.RESOURCE_OWNER_PASSWORD
        )

        then:
        config.providerId() == "keycloak"
        config.tokenUrl() == "https://keycloak.example.com/realms/my-realm/protocol/openid-connect/token"
        config.authorizeUrl() == "https://keycloak.example.com/realms/my-realm/protocol/openid-connect/auth"
        config.userinfoUrl() == "https://keycloak.example.com/realms/my-realm/protocol/openid-connect/userinfo"
        config.deviceCodeUrl() == "https://keycloak.example.com/realms/my-realm/protocol/openid-connect/auth/device"
        config.clientId() == "my-client"
        config.clientSecret() == "my-secret"
        config.flowType() == OAuthFlowType.RESOURCE_OWNER_PASSWORD
        config.scopes() == ["openid", "profile"]
    }

    def "config supports AUTHORIZATION_CODE flow type"() {
        when:
        OAuthProviderConfig config = KeycloakOAuthProvider.config(
                "https://keycloak.example.com", "test-realm",
                "client-1", null, "openid",
                OAuthFlowType.AUTHORIZATION_CODE
        )

        then:
        config.flowType() == OAuthFlowType.AUTHORIZATION_CODE
        config.callbackPort() == 1457
        config.callbackPath() == "/oauth-callback"
    }

    def "config supports DEVICE_CODE flow type"() {
        when:
        OAuthProviderConfig config = KeycloakOAuthProvider.config(
                "https://keycloak.example.com", "test-realm",
                "client-1", null, "openid",
                OAuthFlowType.DEVICE_CODE
        )

        then:
        config.flowType() == OAuthFlowType.DEVICE_CODE
        config.deviceCodeUrl() != null
        config.deviceCodeUrl().contains("/auth/device")
    }

    def "config with null client secret works for public clients"() {
        when:
        OAuthProviderConfig config = KeycloakOAuthProvider.config(
                "https://keycloak.example.com", "public-realm",
                "public-client", null, "openid email",
                OAuthFlowType.AUTHORIZATION_CODE
        )

        then:
        config.clientSecret() == null
        config.clientId() == "public-client"
    }

    def "config handles multiple scopes"() {
        when:
        OAuthProviderConfig config = KeycloakOAuthProvider.config(
                "https://keycloak.example.com", "realm",
                "client", "secret", "openid profile email offline_access",
                OAuthFlowType.RESOURCE_OWNER_PASSWORD
        )

        then:
        config.scopes() == ["openid", "profile", "email", "offline_access"]
    }
}
