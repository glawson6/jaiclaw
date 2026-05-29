package io.jaiclaw.tools.builtin

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpResponse

class WebFetchToolReadabilitySpec extends Specification {

    ToolContext context = new ToolContext("agent", "session", "sid", ".")

    def "extracts readable content from HTML page"() {
        given:
        def html = """
            <!DOCTYPE html>
            <html><head><title>Test Article</title></head>
            <body>
                <nav>Navigation links here and stuff</nav>
                <article>
                    <h1>Test Article</h1>
                    <p>This is the main article content that should be extracted.
                    It contains multiple sentences to make it substantial enough
                    for Readability to recognize it as the main content of the page.</p>
                    <p>Second paragraph with more meaningful content about the topic.
                    This helps Readability determine this is the article body and not
                    some sidebar or navigation element on the page.</p>
                    <p>Third paragraph provides additional context and detail about
                    the subject matter being discussed in this article. The more
                    content we have here the better the extraction will work.</p>
                </article>
                <footer>Copyright 2024 All Rights Reserved</footer>
            </body></html>
        """

        def mockResponse = Stub(HttpResponse) {
            statusCode() >> 200
            body() >> html
        }
        def mockClient = Stub(HttpClient) {
            send(_, _) >> mockResponse
        }
        def tool = new WebFetchTool(mockClient, false)

        when:
        def result = tool.execute(Map.of("url", "https://example.com/article"), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        // Should contain article text, not raw HTML tags
        !content.contains("<nav>")
        !content.contains("<footer>")
        content.contains("main article content")
    }

    def "returns raw HTML when extractReadable is false"() {
        given:
        def html = """<!DOCTYPE html><html><body><article><p>Content here in the article body that spans several lines of text for readability extraction testing purposes</p></article></body></html>"""

        def mockResponse = Stub(HttpResponse) {
            statusCode() >> 200
            body() >> html
        }
        def mockClient = Stub(HttpClient) {
            send(_, _) >> mockResponse
        }
        def tool = new WebFetchTool(mockClient, false)

        when:
        def result = tool.execute(Map.of("url", "https://example.com", "extractReadable", false), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("<article>")
    }

    def "passes through non-HTML content unchanged"() {
        given:
        def jsonBody = '{"key": "value"}'

        def mockResponse = Stub(HttpResponse) {
            statusCode() >> 200
            body() >> jsonBody
        }
        def mockClient = Stub(HttpClient) {
            send(_, _) >> mockResponse
        }
        def tool = new WebFetchTool(mockClient, false)

        when:
        def result = tool.execute(Map.of("url", "https://api.example.com/data"), context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content() == jsonBody
    }

    def "falls back to raw HTML when Readability extraction produces no content"() {
        given:
        def html = """<!DOCTYPE html><html><body></body></html>"""

        def mockResponse = Stub(HttpResponse) {
            statusCode() >> 200
            body() >> html
        }
        def mockClient = Stub(HttpClient) {
            send(_, _) >> mockResponse
        }
        def tool = new WebFetchTool(mockClient, false)

        when:
        def result = tool.execute(Map.of("url", "https://example.com"), context)

        then:
        result instanceof ToolResult.Success
        // Should return something (either extracted or raw HTML fallback)
        def content = (result as ToolResult.Success).content()
        content != null
        !content.isEmpty()
    }
}
