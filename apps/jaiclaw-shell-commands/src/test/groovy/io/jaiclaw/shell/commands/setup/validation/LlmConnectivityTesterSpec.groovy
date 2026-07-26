package io.jaiclaw.shell.commands.setup.validation

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import spock.lang.Specification

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class LlmConnectivityTesterSpec extends Specification {

    RestClient.Builder builder = RestClient.builder()
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build()
    RestClient restClient = builder.build()
    LlmConnectivityTester tester = new LlmConnectivityTester(restClient)

    def "test OpenAI returns success on 200"() {
        given:
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess('{"ok":true}', MediaType.APPLICATION_JSON))

        when:
        def result = tester.test("openai", "sk-test", "gpt-4o", null)

        then:
        result.success()
        result.message() == "Connection successful"
    }

    def "test OpenAI returns failure on HTTP error"() {
        given:
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        when:
        def result = tester.test("openai", "bad-key", "gpt-4o", null)

        then:
        !result.success()
        result.message().contains("401")
    }

    def "test Anthropic returns success on 200"() {
        given:
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withSuccess('{"ok":true}', MediaType.APPLICATION_JSON))

        when:
        def result = tester.test("anthropic", "sk-ant-test", "claude-sonnet-4-6", null)

        then:
        result.success()
    }

    def "test Ollama calls correct URL"() {
        given:
        server.expect(requestTo("http://myhost:11434/api/chat"))
                .andRespond(withSuccess('{"ok":true}', MediaType.APPLICATION_JSON))

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
