package io.jaiclaw.shell.commands.setup.validation

import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import spock.lang.Specification

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class TelegramTokenValidatorSpec extends Specification {

    RestClient.Builder builder = RestClient.builder()
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build()
    RestClient restClient = builder.build()
    TelegramTokenValidator validator = new TelegramTokenValidator(restClient)

    def "returns valid result with bot username on success"() {
        given:
        server.expect(requestTo("https://api.telegram.org/bot123%3AABC/getMe"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess('{"ok":true,"result":{"username":"my_test_bot"}}', MediaType.APPLICATION_JSON))

        when:
        def result = validator.validate("123:ABC")

        then:
        result.valid()
        result.botUsername() == "my_test_bot"
        result.message().contains("@my_test_bot")
    }

    def "returns invalid result when API returns ok=false"() {
        given:
        server.expect(requestTo("https://api.telegram.org/botbad-token/getMe"))
                .andRespond(withSuccess('{"ok":false}', MediaType.APPLICATION_JSON))

        when:
        def result = validator.validate("bad-token")

        then:
        !result.valid()
        result.botUsername() == null
    }

    def "returns invalid result on HTTP error"() {
        given:
        server.expect(requestTo("https://api.telegram.org/botbad-token/getMe"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED))

        when:
        def result = validator.validate("bad-token")

        then:
        !result.valid()
        result.message().contains("Validation failed")
    }
}
