package io.jaiclaw.identity.oauth

import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class ResourceOwnerPasswordFlowSpec extends Specification {

    HttpClient mockHttpClient = Mock()
    ResourceOwnerPasswordFlow flow = new ResourceOwnerPasswordFlow(mockHttpClient)

    def "requestToken sends password grant and returns flow result"() {
        given:
        def config = new OAuthProviderConfig(
                "test-provider", null, "https://auth.example.com/token",
                null, null, "test-client", "test-secret", null, 0, null,
                ["openid", "profile"], OAuthFlowType.RESOURCE_OWNER_PASSWORD
        )
        def responseBody = """
            {
                "access_token": "access-123",
                "refresh_token": "refresh-456",
                "expires_in": 3600,
                "token_type": "Bearer"
            }
        """
        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 200
            body() >> responseBody
        }
        mockHttpClient.send(_ as HttpRequest, _ as HttpResponse.BodyHandler) >> mockResponse

        when:
        OAuthFlowResult result = flow.requestToken(config, "user@example.com", "password123")

        then:
        result.accessToken() == "access-123"
        result.refreshToken() == "refresh-456"
        result.expiresAt() > 0
        result.email() == "user@example.com"
        result.clientId() == "test-client"
    }

    def "requestToken with userinfo endpoint fetches email"() {
        given:
        def config = new OAuthProviderConfig(
                "test-provider", null, "https://auth.example.com/token",
                "https://auth.example.com/userinfo", null, "test-client", null, null, 0, null,
                ["openid"], OAuthFlowType.RESOURCE_OWNER_PASSWORD
        )
        def tokenResponse = Mock(HttpResponse) {
            statusCode() >> 200
            body() >> '{"access_token":"at-1","refresh_token":"rt-1","expires_in":7200}'
        }
        def userinfoResponse = Mock(HttpResponse) {
            statusCode() >> 200
            body() >> '{"email":"real@example.com","sub":"user-001","preferred_username":"realuser"}'
        }
        mockHttpClient.send(_ as HttpRequest, _ as HttpResponse.BodyHandler) >>> [tokenResponse, userinfoResponse]

        when:
        OAuthFlowResult result = flow.requestToken(config, "user", "pass")

        then:
        result.accessToken() == "at-1"
        result.email() == "real@example.com"
        result.accountId() == "user-001"
    }

    def "requestToken throws when token URL is missing"() {
        given:
        def config = new OAuthProviderConfig(
                "bad-provider", null, null, null, null, "client", null, null, 0, null,
                [], OAuthFlowType.RESOURCE_OWNER_PASSWORD
        )

        when:
        flow.requestToken(config, "user", "pass")

        then:
        thrown(OAuthFlowException)
    }

    def "requestToken throws on HTTP error response"() {
        given:
        def config = new OAuthProviderConfig(
                "test", null, "https://auth.example.com/token",
                null, null, "client", null, null, 0, null,
                [], OAuthFlowType.RESOURCE_OWNER_PASSWORD
        )
        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 401
            body() >> '{"error":"invalid_grant","error_description":"Bad credentials"}'
        }
        mockHttpClient.send(_ as HttpRequest, _ as HttpResponse.BodyHandler) >> mockResponse

        when:
        flow.requestToken(config, "user", "wrong-pass")

        then:
        OAuthFlowException e = thrown()
        e.message.contains("401")
    }

    def "requestToken throws when access_token is missing from response"() {
        given:
        def config = new OAuthProviderConfig(
                "test", null, "https://auth.example.com/token",
                null, null, "client", null, null, 0, null,
                [], OAuthFlowType.RESOURCE_OWNER_PASSWORD
        )
        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 200
            body() >> '{"token_type":"Bearer"}'
        }
        mockHttpClient.send(_ as HttpRequest, _ as HttpResponse.BodyHandler) >> mockResponse

        when:
        flow.requestToken(config, "user", "pass")

        then:
        OAuthFlowException e = thrown()
        e.message.contains("access_token")
    }

    def "requestToken includes client_secret when present"() {
        given:
        def config = new OAuthProviderConfig(
                "test", null, "https://auth.example.com/token",
                null, null, "client-id", "client-secret", null, 0, null,
                ["openid"], OAuthFlowType.RESOURCE_OWNER_PASSWORD
        )
        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 200
            body() >> '{"access_token":"tok","expires_in":3600}'
        }
        HttpRequest capturedRequest = null
        mockHttpClient.send(_ as HttpRequest, _ as HttpResponse.BodyHandler) >> { args ->
            capturedRequest = args[0]
            return mockResponse
        }

        when:
        flow.requestToken(config, "user", "pass")

        then:
        capturedRequest != null
        capturedRequest.uri().toString() == "https://auth.example.com/token"
    }

    def "requestToken includes scopes when present"() {
        given:
        def config = new OAuthProviderConfig(
                "test", null, "https://auth.example.com/token",
                null, null, "client", null, null, 0, null,
                ["openid", "profile", "email"], OAuthFlowType.RESOURCE_OWNER_PASSWORD
        )
        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 200
            body() >> '{"access_token":"tok","expires_in":3600}'
        }
        mockHttpClient.send(_ as HttpRequest, _ as HttpResponse.BodyHandler) >> mockResponse

        when:
        OAuthFlowResult result = flow.requestToken(config, "user", "pass")

        then:
        result.accessToken() == "tok"
    }
}
