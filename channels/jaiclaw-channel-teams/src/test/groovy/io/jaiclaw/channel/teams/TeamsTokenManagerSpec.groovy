package io.jaiclaw.channel.teams

import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class TeamsTokenManagerSpec extends Specification {

    RestTemplate restTemplate = Mock()

    def "fetches and caches access token"() {
        given:
        def tokenManager = new TeamsTokenManager("app-id", "app-secret", restTemplate)
        def tokenResponse = '{"access_token": "test-token-123", "expires_in": 3600}'

        when:
        def token1 = tokenManager.getAccessToken()
        def token2 = tokenManager.getAccessToken()

        then:
        // First call fetches, second call uses cache
        1 * restTemplate.postForEntity(_, _, String.class) >> new ResponseEntity<>(tokenResponse, HttpStatus.OK)
        token1 == "test-token-123"
        token2 == "test-token-123"
    }

    def "refreshes token when expired"() {
        given:
        def tokenManager = new TeamsTokenManager("app-id", "app-secret", restTemplate)
        // Return a token that expires immediately (0 seconds)
        def expiredResponse = '{"access_token": "expired-token", "expires_in": 0}'
        def freshResponse = '{"access_token": "fresh-token", "expires_in": 3600}'

        when:
        def token1 = tokenManager.getAccessToken()
        def token2 = tokenManager.getAccessToken()

        then:
        // Both calls should fetch because the first token expires immediately
        2 * restTemplate.postForEntity(_, _, String.class) >>> [
            new ResponseEntity<>(expiredResponse, HttpStatus.OK),
            new ResponseEntity<>(freshResponse, HttpStatus.OK)
        ]
        token1 == "expired-token"
        token2 == "fresh-token"
    }

    def "throws on token fetch failure"() {
        given:
        def tokenManager = new TeamsTokenManager("app-id", "app-secret", restTemplate)

        when:
        tokenManager.getAccessToken()

        then:
        1 * restTemplate.postForEntity(_, _, String.class) >> new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR)
        thrown(RuntimeException)
    }
}
