package io.jaiclaw.identity.oauth.provider

import io.jaiclaw.identity.oauth.OAuthFlowType
import io.jaiclaw.identity.oauth.OAuthProviderConfig
import spock.lang.Specification

class SpringAuthServerOAuthProviderSpec extends Specification {

    def "config with explicit parameters derives correct endpoints"() {
        when:
        OAuthProviderConfig config = SpringAuthServerOAuthProvider.config(
                "https://auth.example.com", "my-client", "my-secret",
                "openid profile", OAuthFlowType.RESOURCE_OWNER_PASSWORD
        )

        then:
        config.providerId() == "spring-auth-server"
        config.tokenUrl() == "https://auth.example.com/oauth2/token"
        config.authorizeUrl() == "https://auth.example.com/oauth2/authorize"
        config.userinfoUrl() == "https://auth.example.com/userinfo"
        config.deviceCodeUrl() == "https://auth.example.com/oauth2/device_authorization"
        config.clientId() == "my-client"
        config.clientSecret() == "my-secret"
        config.flowType() == OAuthFlowType.RESOURCE_OWNER_PASSWORD
        config.scopes() == ["openid", "profile"]
    }

    def "config supports AUTHORIZATION_CODE flow type"() {
        when:
        OAuthProviderConfig config = SpringAuthServerOAuthProvider.config(
                "http://localhost:8082", "client-1", null,
                "openid", OAuthFlowType.AUTHORIZATION_CODE
        )

        then:
        config.flowType() == OAuthFlowType.AUTHORIZATION_CODE
        config.callbackPort() == 1458
        config.callbackPath() == "/oauth-callback"
        config.authorizeUrl() == "http://localhost:8082/oauth2/authorize"
    }

    def "config supports DEVICE_CODE flow type"() {
        when:
        OAuthProviderConfig config = SpringAuthServerOAuthProvider.config(
                "http://localhost:8082", "client-1", null,
                "openid", OAuthFlowType.DEVICE_CODE
        )

        then:
        config.flowType() == OAuthFlowType.DEVICE_CODE
        config.deviceCodeUrl() == "http://localhost:8082/oauth2/device_authorization"
    }

    def "config with null client secret works for public clients"() {
        when:
        OAuthProviderConfig config = SpringAuthServerOAuthProvider.config(
                "http://localhost:8082", "public-client", null,
                "openid email", OAuthFlowType.AUTHORIZATION_CODE
        )

        then:
        config.clientSecret() == null
        config.clientId() == "public-client"
    }

    def "config handles multiple scopes"() {
        when:
        OAuthProviderConfig config = SpringAuthServerOAuthProvider.config(
                "http://localhost:8082", "client", "secret",
                "openid profile email", OAuthFlowType.RESOURCE_OWNER_PASSWORD
        )

        then:
        config.scopes() == ["openid", "profile", "email"]
    }
}
