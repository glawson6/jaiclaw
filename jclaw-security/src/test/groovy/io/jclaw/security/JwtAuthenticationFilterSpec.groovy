package io.jclaw.security

import io.jclaw.core.tenant.DefaultTenantContext
import io.jclaw.core.tenant.TenantContextHolder
import io.jclaw.core.tool.ToolProfile
import io.jclaw.core.tool.ToolProfileHolder
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

class JwtAuthenticationFilterSpec extends Specification {

    def cleanup() {
        SecurityContextHolder.clearContext()
        TenantContextHolder.clear()
        ToolProfileHolder.clear()
    }

    def "sets security context, tenant context, and tool profile on valid JWT"() {
        given:
        def tenantContext = new DefaultTenantContext("t1", "Tenant One", [:])
        def validated = new JwtTokenValidator.ValidatedToken(tenantContext, "user@test.com", ["admin"])
        def validator = Mock(JwtTokenValidator) {
            validate("valid-token") >> Optional.of(validated)
        }
        def resolver = new RoleToolProfileResolver([admin: "FULL"], "MINIMAL")
        def filter = new JwtAuthenticationFilter(validator, resolver)

        def request = Mock(HttpServletRequest) {
            getHeader("Authorization") >> "Bearer valid-token"
        }
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * chain.doFilter(request, response)

        and: "contexts are cleared after request"
        TenantContextHolder.get() == null
        ToolProfileHolder.get() == null
    }

    def "passes through without auth header"() {
        given:
        def validator = Mock(JwtTokenValidator)
        def filter = new JwtAuthenticationFilter(validator)

        def request = Mock(HttpServletRequest) {
            getHeader("Authorization") >> null
        }
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
        0 * validator.validate(_)
    }

    def "passes through with invalid token"() {
        given:
        def validator = Mock(JwtTokenValidator) {
            validate("bad-token") >> Optional.empty()
        }
        def filter = new JwtAuthenticationFilter(validator)

        def request = Mock(HttpServletRequest) {
            getHeader("Authorization") >> "Bearer bad-token"
        }
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
        SecurityContextHolder.getContext().getAuthentication() == null
    }

    def "works without RoleToolProfileResolver (backward compatible)"() {
        given:
        def tenantContext = new DefaultTenantContext("t1", "Tenant One", [:])
        def validated = new JwtTokenValidator.ValidatedToken(tenantContext, "user@test.com", ["admin"])
        def validator = Mock(JwtTokenValidator) {
            validate("valid-token") >> Optional.of(validated)
        }
        def filter = new JwtAuthenticationFilter(validator)

        def request = Mock(HttpServletRequest) {
            getHeader("Authorization") >> "Bearer valid-token"
        }
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain)

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
        ToolProfileHolder.get() == null
    }

    def "clears all contexts even when chain throws"() {
        given:
        def tenantContext = new DefaultTenantContext("t1", "Tenant One", [:])
        def validated = new JwtTokenValidator.ValidatedToken(tenantContext, "user@test.com", ["admin"])
        def validator = Mock(JwtTokenValidator) {
            validate("valid-token") >> Optional.of(validated)
        }
        def resolver = new RoleToolProfileResolver([admin: "FULL"], "MINIMAL")
        def filter = new JwtAuthenticationFilter(validator, resolver)

        def request = Mock(HttpServletRequest) {
            getHeader("Authorization") >> "Bearer valid-token"
        }
        def response = Mock(HttpServletResponse)
        def chain = Mock(FilterChain) {
            doFilter(_, _) >> { throw new RuntimeException("boom") }
        }

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        thrown(RuntimeException)
        TenantContextHolder.get() == null
        ToolProfileHolder.get() == null
    }
}
