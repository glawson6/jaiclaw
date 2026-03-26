package io.jaiclaw.perplexity

import io.jaiclaw.perplexity.model.*
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class PerplexityClientSpec extends Specification {

    HttpClient mockHttp = Mock()
    PerplexityClient client = new PerplexityClient("test-api-key", mockHttp)

    def "chat sends correct request and parses response"() {
        given:
        def responseJson = '''{
            "id": "chatcmpl-123",
            "model": "sonar-pro",
            "choices": [{"index": 0, "finish_reason": "stop", "message": {"role": "assistant", "content": "Test answer"}}],
            "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30},
            "citations": ["https://example.com"],
            "search_results": [{"title": "Example", "url": "https://example.com", "snippet": "A snippet"}],
            "related_questions": ["What else?"],
            "images": []
        }'''

        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 200
            body() >> responseJson
        }

        when:
        def request = SonarRequest.builder()
                .model("sonar-pro")
                .messages([new Message("user", "What is Java?")])
                .temperature(0.2)
                .maxTokens(4096)
                .build()
        def result = client.chat(request)

        then:
        1 * mockHttp.send({ HttpRequest req ->
            req.uri().toString() == "https://api.perplexity.ai/chat/completions" &&
            req.headers().firstValue("Authorization").get() == "Bearer test-api-key" &&
            req.headers().firstValue("Content-Type").get() == "application/json" &&
            req.method() == "POST"
        }, _) >> mockResponse

        result.id() == "chatcmpl-123"
        result.model() == "sonar-pro"
        result.choices().size() == 1
        result.choices()[0].message().content() == "Test answer"
        result.usage().totalTokens() == 30
        result.citations() == ["https://example.com"]
        result.searchResults().size() == 1
    }

    def "chat request body includes correct fields"() {
        given:
        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 200
            body() >> '{"id":"1","model":"sonar-pro","choices":[],"usage":{"prompt_tokens":0,"completion_tokens":0,"total_tokens":0}}'
        }
        mockHttp.send(_, _) >> mockResponse

        when:
        def request = SonarRequest.builder()
                .model("sonar-pro")
                .messages([new Message("user", "test")])
                .temperature(0.5)
                .maxTokens(1024)
                .searchDomainFilter(["example.com", "test.org"])
                .searchRecencyFilter("week")
                .returnImages(true)
                .returnRelatedQuestions(true)
                .build()

        // Verify request is built correctly
        client.chat(request)

        then:
        request.model() == "sonar-pro"
        request.messages().size() == 1
        request.temperature() == 0.5
        request.maxTokens() == 1024
        request.searchDomainFilter() == ["example.com", "test.org"]
        request.searchRecencyFilter() == "week"
        request.returnImages()
        request.returnRelatedQuestions()
    }

    def "search sends correct request and parses response"() {
        given:
        def responseJson = '''{
            "results": [
                {"title": "Result 1", "url": "https://r1.com", "snippet": "Snippet 1"},
                {"title": "Result 2", "url": "https://r2.com", "snippet": "Snippet 2"}
            ],
            "total_results": 100
        }'''

        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 200
            body() >> responseJson
        }

        when:
        def request = new SearchApiRequest("test query", 5, "week", ["example.com"])
        def result = client.search(request)

        then:
        1 * mockHttp.send({ HttpRequest req ->
            req.uri().toString() == "https://api.perplexity.ai/search"
        }, _) >> mockResponse

        result.totalResults() == 100
        result.results().size() == 2
        result.results()[0].title() == "Result 1"
        result.results()[1].url() == "https://r2.com"
    }

    def "agent sends correct request and parses response"() {
        given:
        def responseJson = '''{
            "id": "agent-123",
            "content": "Research findings here",
            "citations": [{"url": "https://source.com", "title": "Source", "snippet": "snip"}],
            "steps": [{"tool_name": "search", "input": "query", "output": "results"}],
            "usage": {"prompt_tokens": 50, "completion_tokens": 200, "total_tokens": 250}
        }'''

        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 200
            body() >> responseJson
        }

        when:
        def request = AgentRequest.builder()
                .preset("deep-research")
                .model("sonar-pro")
                .messages([new Message("user", "Research topic")])
                .maxTokens(4096)
                .build()
        def result = client.agent(request)

        then:
        1 * mockHttp.send({ HttpRequest req ->
            req.uri().toString() == "https://api.perplexity.ai/v1/agent"
        }, _) >> mockResponse

        result.id() == "agent-123"
        result.content() == "Research findings here"
        result.citations().size() == 1
        result.citations()[0].title() == "Source"
        result.steps().size() == 1
        result.steps()[0].toolName() == "search"
        result.usage().totalTokens() == 250
    }

    def "throws PerplexityApiException on 4xx errors"() {
        given:
        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 401
            body() >> '{"error": "Unauthorized"}'
        }
        mockHttp.send(_, _) >> mockResponse

        when:
        client.chat(SonarRequest.builder()
                .messages([new Message("user", "test")])
                .build())

        then:
        def e = thrown(PerplexityApiException)
        e.statusCode == 401
        e.responseBody == '{"error": "Unauthorized"}'
    }

    def "throws PerplexityApiException on 5xx errors"() {
        given:
        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 500
            body() >> '{"error": "Internal server error"}'
        }
        mockHttp.send(_, _) >> mockResponse

        when:
        client.search(new SearchApiRequest("test", null, null, null))

        then:
        def e = thrown(PerplexityApiException)
        e.statusCode == 500
    }

    def "handles unknown fields gracefully"() {
        given:
        def responseJson = '''{
            "id": "1",
            "model": "sonar-pro",
            "choices": [],
            "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
            "some_unknown_field": "should be ignored"
        }'''

        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 200
            body() >> responseJson
        }
        mockHttp.send(_, _) >> mockResponse

        when:
        def result = client.chat(SonarRequest.builder()
                .messages([new Message("user", "test")])
                .build())

        then:
        noExceptionThrown()
        result.id() == "1"
    }

    def "SonarRequest builder has sensible defaults"() {
        when:
        def request = SonarRequest.builder()
                .messages([new Message("user", "test")])
                .build()

        then:
        request.model() == "sonar-pro"
        !request.stream()
        !request.returnImages()
        !request.returnRelatedQuestions()
        request.temperature() == null
        request.maxTokens() == null
    }

    def "AgentRequest builder has sensible defaults"() {
        when:
        def request = AgentRequest.builder()
                .messages([new Message("user", "test")])
                .build()

        then:
        request.preset() == "pro-search"
        request.model() == null
        request.maxTokens() == null
        request.tools() == null
    }
}
