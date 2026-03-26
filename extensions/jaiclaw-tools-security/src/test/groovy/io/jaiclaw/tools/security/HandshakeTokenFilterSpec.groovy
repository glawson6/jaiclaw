package io.jaiclaw.tools.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

import java.io.PrintWriter
import java.io.StringWriter

class HandshakeTokenFilterSpec extends Specification {

    def setup() {
        SecurityContextHolder.clearContext()
    }

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "shouldNotFilter skips handshake endpoints"() {
        given:
        def filter = new HandshakeTokenFilter(new HandshakeSessionStore(), "security")
        def request = Mock(HttpServletRequest) {
            getRequestURI() >> "/mcp/security/negotiate"
        }

        expect:
        filter.shouldNotFilter(request)
    }

    def "shouldNotFilter skips non-MCP paths"() {
        given:
        def filter = new HandshakeTokenFilter(new HandshakeSessionStore(), "security")
        def request = Mock(HttpServletRequest) {
            getRequestURI() >> "/api/health"
        }

        expect:
        filter.shouldNotFilter(request)
    }

    def "shouldNotFilter does NOT skip other MCP paths"() {
        given:
        def filter = new HandshakeTokenFilter(new HandshakeSessionStore(), "security")
        def request = Mock(HttpServletRequest) {
            getRequestURI() >> "/mcp/protected-server/tool"
        }

        expect:
        !filter.shouldNotFilter(request)
    }

    def "returns 401 when no Authorization header"() {
        given:
        def store = new HandshakeSessionStore()
        def filter = new HandshakeTokenFilter(store, "security")
        def request = Mock(HttpServletRequest) {
            getRequestURI() >> "/mcp/protected/tool"
            getHeader("Authorization") >> null
        }
        def writer = new StringWriter()
        def response = Mock(HttpServletResponse) {
            getWriter() >> new PrintWriter(writer)
        }
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * response.setStatus(401)
        0 * chain.doFilter(_, _)
        writer.toString().contains("Missing session token")
    }

    def "returns 401 when no Bearer prefix"() {
        given:
        def store = new HandshakeSessionStore()
        def filter = new HandshakeTokenFilter(store, "security")
        def request = Mock(HttpServletRequest) {
            getRequestURI() >> "/mcp/protected/tool"
            getHeader("Authorization") >> "Basic abc123"
        }
        def writer = new StringWriter()
        def response = Mock(HttpServletResponse) {
            getWriter() >> new PrintWriter(writer)
        }
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * response.setStatus(401)
        0 * chain.doFilter(_, _)
    }

    def "returns 401 for invalid token"() {
        given:
        def store = new HandshakeSessionStore()
        def filter = new HandshakeTokenFilter(store, "security")
        def request = Mock(HttpServletRequest) {
            getRequestURI() >> "/mcp/protected/tool"
            getHeader("Authorization") >> "Bearer invalid-token-xyz"
        }
        def writer = new StringWriter()
        def response = Mock(HttpServletResponse) {
            getWriter() >> new PrintWriter(writer)
        }
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * response.setStatus(401)
        0 * chain.doFilter(_, _)
        writer.toString().contains("Invalid or expired session token")
    }

    def "passes through with valid token and sets security context"() {
        given:
        def store = new HandshakeSessionStore()
        def session = store.create()
        session.identityVerified = true
        session.verifiedSubject = "test-user"
        session.sessionToken = "valid-token-123"
        session.completed = true

        def filter = new HandshakeTokenFilter(store, "security")
        def request = Mock(HttpServletRequest) {
            getRequestURI() >> "/mcp/protected/tool"
            getHeader("Authorization") >> "Bearer valid-token-123"
        }
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
        0 * response.setStatus(401)
    }

    def "clears security context after filter completes"() {
        given:
        def store = new HandshakeSessionStore()
        def session = store.create()
        session.identityVerified = true
        session.verifiedSubject = "test-user"
        session.sessionToken = "valid-token-abc"
        session.completed = true

        def filter = new HandshakeTokenFilter(store, "security")
        def request = Mock(HttpServletRequest) {
            getRequestURI() >> "/mcp/protected/tool"
            getHeader("Authorization") >> "Bearer valid-token-abc"
        }
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        SecurityContextHolder.getContext().getAuthentication() == null
    }

    def "skips authentication when already authenticated"() {
        given:
        def store = new HandshakeSessionStore()
        def filter = new HandshakeTokenFilter(store, "security")

        // Pre-set authentication
        def auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "existing-user", null, [])
        SecurityContextHolder.getContext().setAuthentication(auth)

        def request = Mock(HttpServletRequest) {
            getRequestURI() >> "/mcp/protected/tool"
        }
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
        0 * response.setStatus(_)
    }
}
