package io.jclaw.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

class ApiKeyAuthenticationFilterSpec extends Specification {

    static final String VALID_KEY = "jclaw_ak_test1234567890abcdef1234"

    ApiKeyProvider provider = Stub(ApiKeyProvider) {
        getResolvedKey() >> VALID_KEY
    }
    ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(provider)

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "skips /api/health endpoint"() {
        given:
        def request = mockRequest("/api/health")
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        expect:
        filter.shouldNotFilter(request)
    }

    def "skips webhook endpoints"() {
        given:
        def request = mockRequest("/webhook/telegram")

        expect:
        filter.shouldNotFilter(request)
    }

    def "does not skip /api/chat"() {
        given:
        def request = mockRequest("/api/chat")

        expect:
        !filter.shouldNotFilter(request)
    }

    def "does not skip /mcp/tools"() {
        given:
        def request = mockRequest("/mcp/tools")

        expect:
        !filter.shouldNotFilter(request)
    }

    def "returns 401 on missing API key"() {
        given:
        def request = mockRequest("/api/chat")
        def response = Mock(HttpServletResponse)
        def writer = new PrintWriter(new StringWriter())
        response.getWriter() >> writer
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * response.setStatus(401)
        1 * response.setContentType("application/json")
        0 * chain.doFilter(_, _)
    }

    def "returns 401 on wrong API key"() {
        given:
        def request = mockRequest("/api/chat", "wrong-key")
        def response = Mock(HttpServletResponse)
        def writer = new PrintWriter(new StringWriter())
        response.getWriter() >> writer
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * response.setStatus(401)
        0 * chain.doFilter(_, _)
    }

    def "passes with correct API key on /api/chat"() {
        given:
        def request = mockRequest("/api/chat", VALID_KEY)
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
        0 * response.setStatus(401)
    }

    def "passes with correct API key on /mcp/tools"() {
        given:
        def request = mockRequest("/mcp/tools", VALID_KEY)
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
    }

    def "accepts api_key query parameter"() {
        given:
        def request = Mock(HttpServletRequest) {
            getRequestURI() >> "/api/chat"
            getHeader("X-API-Key") >> null
            getParameter("api_key") >> VALID_KEY
        }
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
    }

    def "sets authentication principal to api-key-user"() {
        given:
        def request = mockRequest("/api/chat", VALID_KEY)
        def response = Mock(HttpServletResponse)
        def authed = null
        def chain = Mock(FilterChain) {
            doFilter(_, _) >> {
                authed = SecurityContextHolder.getContext().getAuthentication()
            }
        }

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        authed != null
        authed.principal == "api-key-user"
    }

    private HttpServletRequest mockRequest(String uri, String apiKey = null) {
        Mock(HttpServletRequest) {
            getRequestURI() >> uri
            getHeader("X-API-Key") >> apiKey
            getParameter("api_key") >> null
        }
    }
}
