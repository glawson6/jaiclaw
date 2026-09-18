package io.jaiclaw.gateway.mcp.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import spock.lang.Specification

class McpBearerChallengeFilterSpec extends Specification {

    static final String METADATA =
            "https://jaiclaw.example.com/.well-known/oauth-protected-resource"

    def filter = new McpBearerChallengeFilter(METADATA, ["/mcp/**"])
    def chain = Mock(FilterChain)

    def "adds the discovery pointer to a 401 from an MCP endpoint"() {
        given:
        def request = mockRequest("/mcp/tools/list")
        def response = Mock(HttpServletResponse) {
            getStatus() >> 401
            isCommitted() >> false
            getHeader("WWW-Authenticate") >> null
        }

        when:
        filter.doFilterInternal(request, response, chain)

        then: "without this a client has no way to learn WHERE the document lives"
        1 * response.setHeader("WWW-Authenticate",
                'Bearer resource_metadata="' + METADATA + '"')
    }

    def "preserves an existing challenge and appends the pointer"() {
        given:
        def request = mockRequest("/mcp/tools/list")
        def response = Mock(HttpServletResponse) {
            getStatus() >> 401
            isCommitted() >> false
            getHeader("WWW-Authenticate") >> 'Bearer error="invalid_token"'
        }

        when:
        filter.doFilterInternal(request, response, chain)

        then: "the auth layer's error detail must survive"
        1 * response.setHeader("WWW-Authenticate",
                'Bearer error="invalid_token", resource_metadata="' + METADATA + '"')
    }

    def "does not duplicate a pointer that is already present"() {
        given:
        def request = mockRequest("/mcp/tools/list")
        def response = Mock(HttpServletResponse) {
            getStatus() >> 401
            isCommitted() >> false
            getHeader("WWW-Authenticate") >> 'Bearer resource_metadata="' + METADATA + '"'
        }

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        0 * response.setHeader("WWW-Authenticate", _)
    }

    def "leaves non-401 responses alone"() {
        given:
        def request = mockRequest("/mcp/tools/list")
        def response = Mock(HttpServletResponse) { getStatus() >> status }

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        0 * response.setHeader("WWW-Authenticate", _)

        where:
        status << [200, 403, 404, 500]
    }

    def "cannot set a header on a committed response, and does not try"() {
        given:
        def request = mockRequest("/mcp/tools/list")
        def response = Mock(HttpServletResponse) {
            getStatus() >> 401
            isCommitted() >> true
        }

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        0 * response.setHeader("WWW-Authenticate", _)
    }

    def "only applies to MCP paths"() {
        expect:
        filter.shouldNotFilter(mockRequest(path)) == skipped

        where:
        path              || skipped
        "/mcp/tools/list" || false
        // Spring's /mcp/** matches /mcp itself. Correct: the server-list endpoint
        // is an MCP surface too, and a client hitting it deserves the same hint.
        "/mcp"            || false
        "/api/chat"       || true
        "/api/health"     || true
        "/"               || true
    }

    private HttpServletRequest mockRequest(String uri) {
        Mock(HttpServletRequest) { getRequestURI() >> uri }
    }
}
