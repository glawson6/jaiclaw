package io.jclaw.shell.commands.setup.validation

import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class LlmConnectivityTesterSpec extends Specification {

    RestTemplate restTemplate = Mock()
    LlmConnectivityTester tester = new LlmConnectivityTester(restTemplate)

    def "test OpenAI returns success on 200"() {
        given:
        restTemplate.postForEntity(
                "https://api.openai.com/v1/chat/completions",
                _ as HttpEntity,
                String) >> new ResponseEntity<>("ok", HttpStatus.OK)

        when:
        def result = tester.test("openai", "sk-test", "gpt-4o", null)

        then:
        result.success()
        result.message() == "Connection successful"
    }

    def "test OpenAI returns failure on HTTP error"() {
        given:
        restTemplate.postForEntity(
                "https://api.openai.com/v1/chat/completions",
                _ as HttpEntity,
                String) >> { throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized") }

        when:
        def result = tester.test("openai", "bad-key", "gpt-4o", null)

        then:
        !result.success()
        result.message().contains("401")
    }

    def "test Anthropic returns success on 200"() {
        given:
        restTemplate.postForEntity(
                "https://api.anthropic.com/v1/messages",
                _ as HttpEntity,
                String) >> new ResponseEntity<>("ok", HttpStatus.OK)

        when:
        def result = tester.test("anthropic", "sk-ant-test", "claude-sonnet-4-6", null)

        then:
        result.success()
    }

    def "test Ollama calls correct URL"() {
        given:
        restTemplate.postForEntity(
                "http://myhost:11434/api/chat",
                _ as HttpEntity,
                String) >> new ResponseEntity<>("ok", HttpStatus.OK)

        when:
        def result = tester.test("ollama", null, "llama3", "http://myhost:11434")

        then:
        result.success()
    }

    def "test returns failure for unknown provider"() {
        when:
        def result = tester.test("unknown", null, "model", null)

        then:
        !result.success()
        result.message().contains("Unknown provider")
    }
}
