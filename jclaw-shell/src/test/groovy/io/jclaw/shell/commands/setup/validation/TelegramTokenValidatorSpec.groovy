package io.jclaw.shell.commands.setup.validation

import org.springframework.web.client.HttpClientErrorException
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class TelegramTokenValidatorSpec extends Specification {

    RestTemplate restTemplate = Mock()
    TelegramTokenValidator validator = new TelegramTokenValidator(restTemplate)

    def "returns valid result with bot username on success"() {
        given:
        restTemplate.getForObject(
                "https://api.telegram.org/bot{token}/getMe",
                Map,
                "123:ABC") >> [ok: true, result: [username: "my_test_bot"]]

        when:
        def result = validator.validate("123:ABC")

        then:
        result.valid()
        result.botUsername() == "my_test_bot"
        result.message().contains("@my_test_bot")
    }

    def "returns invalid result when API returns ok=false"() {
        given:
        restTemplate.getForObject(
                "https://api.telegram.org/bot{token}/getMe",
                Map,
                "bad-token") >> [ok: false]

        when:
        def result = validator.validate("bad-token")

        then:
        !result.valid()
        result.botUsername() == null
    }

    def "returns invalid result on HTTP error"() {
        given:
        restTemplate.getForObject(
                "https://api.telegram.org/bot{token}/getMe",
                Map,
                "bad-token") >> { throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED) }

        when:
        def result = validator.validate("bad-token")

        then:
        !result.valid()
        result.message().contains("Validation failed")
    }
}
