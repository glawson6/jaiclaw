package io.jclaw.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

import java.io.PrintWriter
import java.io.StringWriter

class RateLimitFilterSpec extends Specification {

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "allows requests under the limit"() {
        given:
        def filter = new RateLimitFilter(5, 60, 3600)
        def request = mockRequest("/api/chat", "10.0.0.1")
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        when:
        filter.doFilter(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
        1 * response.setHeader("X-RateLimit-Limit", "5")
        1 * response.setHeader("X-RateLimit-Remaining", "4")
    }

    def "returns 429 when limit exceeded"() {
        given:
        def filter = new RateLimitFilter(2, 60, 3600)
        def chain = Mock(FilterChain)
        def writer = new StringWriter()
        def printWriter = new PrintWriter(writer)

        when: "send 3 requests (limit is 2)"
        3.times { i ->
            def req = mockRequest("/api/chat", "10.0.0.1")
            def resp = Mock(HttpServletResponse)
            if (i == 2) {
                resp.getWriter() >> printWriter
            }
            filter.doFilter(req, resp, chain)
        }

        then: "first 2 succeed, 3rd is blocked"
        2 * chain.doFilter(_, _)
    }

    def "skips non-api paths"() {
        given:
        def filter = new RateLimitFilter(1, 60, 3600)
        def request = mockRequest("/webhook/telegram", "10.0.0.1")
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        when:
        filter.doFilter(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
        0 * response.setHeader("X-RateLimit-Limit", _)
    }

    def "uses JWT subject as sender key when authenticated"() {
        given:
        def filter = new RateLimitFilter(1, 60, 3600)
        def chain = Mock(FilterChain)
        def auth = new UsernamePasswordAuthenticationToken("user-a", null, [])
        SecurityContextHolder.getContext().setAuthentication(auth)
        def request1 = mockRequest("/api/chat", "10.0.0.1")
        def response1 = Mock(HttpServletResponse)
        def request2 = mockRequest("/api/chat", "10.0.0.2")
        def response2 = Mock(HttpServletResponse) {
            getWriter() >> new PrintWriter(new StringWriter())
        }

        when: "user-a sends first request"
        filter.doFilter(request1, response1, chain)

        then:
        1 * chain.doFilter(request1, response1)

        when: "same JWT subject from different IP — still limited"
        SecurityContextHolder.getContext().setAuthentication(auth)
        filter.doFilter(request2, response2, chain)

        then: "blocked because same jwt subject exceeded limit"
        1 * response2.setStatus(429)
    }

    def "separate senders have independent limits"() {
        given:
        def filter = new RateLimitFilter(1, 60, 3600)
        def chain = Mock(FilterChain)
        def reqA = mockRequest("/api/chat", "10.0.0.1")
        def respA = Mock(HttpServletResponse)
        def reqB = mockRequest("/api/chat", "10.0.0.2")
        def respB = Mock(HttpServletResponse)

        when: "sender A"
        filter.doFilter(reqA, respA, chain)

        then:
        1 * chain.doFilter(reqA, respA)

        when: "sender B"
        filter.doFilter(reqB, respB, chain)

        then: "both succeed"
        1 * chain.doFilter(reqB, respB)
    }

    private HttpServletRequest mockRequest(String uri, String remoteAddr) {
        def request = Mock(HttpServletRequest)
        request.getRequestURI() >> uri
        request.getRemoteAddr() >> remoteAddr
        request.getHeader("X-Forwarded-For") >> null
        return request
    }
}
