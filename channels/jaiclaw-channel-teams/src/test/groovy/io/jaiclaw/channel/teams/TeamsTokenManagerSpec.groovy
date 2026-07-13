package io.jaiclaw.channel.teams

import spock.lang.Specification

class TeamsTokenManagerSpec extends Specification {

    TeamsHttpClient httpClient = Mock()

    def "fetches and caches access token"() {
        given:
        def tokenManager = new TeamsTokenManager("app-id", "app-secret", httpClient)

        when:
        def token1 = tokenManager.getAccessToken()
        def token2 = tokenManager.getAccessToken()

        then:
        // First call fetches, second call uses cache
        1 * httpClient.postForm(_, _) >> '{"access_token": "test-token-123", "expires_in": 3600}'
        token1 == "test-token-123"
        token2 == "test-token-123"
    }

    def "refreshes token when expired"() {
        given:
        def tokenManager = new TeamsTokenManager("app-id", "app-secret", httpClient)

        when:
        def token1 = tokenManager.getAccessToken()
        def token2 = tokenManager.getAccessToken()

        then:
        // Both calls should fetch because the first token expires immediately
        2 * httpClient.postForm(_, _) >>> [
                '{"access_token": "expired-token", "expires_in": 0}',
                '{"access_token": "fresh-token", "expires_in": 3600}'
        ]
        token1 == "expired-token"
        token2 == "fresh-token"
    }

    def "throws on token fetch failure"() {
        given:
        def tokenManager = new TeamsTokenManager("app-id", "app-secret", httpClient)

        when:
        tokenManager.getAccessToken()

        then:
        1 * httpClient.postForm(_, _) >> { throw new RuntimeException("HTTP 500") }
        thrown(RuntimeException)
    }
}
