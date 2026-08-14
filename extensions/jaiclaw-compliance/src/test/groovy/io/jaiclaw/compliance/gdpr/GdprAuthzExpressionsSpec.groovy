package io.jaiclaw.compliance.gdpr

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

class GdprAuthzExpressionsSpec extends Specification {

    GdprAuthzProperties.Roles defaultRoles = GdprAuthzProperties.Roles.DEFAULT
    GdprAuthzExpressions authz = new GdprAuthzExpressions(defaultRoles)

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "unauthenticated principal is denied on operator()"() {
        expect:
        !authz.operator()
    }

    def "principal with the GDPR_OPERATOR authority passes"() {
        given:
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
                "alice", "n/a",
                [new SimpleGrantedAuthority("GDPR_OPERATOR")])

        expect:
        authz.operator()
    }

    def "principal without the GDPR_OPERATOR authority is denied"() {
        given:
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
                "alice", "n/a",
                [new SimpleGrantedAuthority("ROLE_USER")])

        expect:
        !authz.operator()
    }

    def "blank role config short-circuits to true for authenticated principals"() {
        given:
        GdprAuthzProperties.Roles blank = new GdprAuthzProperties.Roles("")
        GdprAuthzExpressions permissive = new GdprAuthzExpressions(blank)
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
                "alice", "n/a", [new SimpleGrantedAuthority("ROLE_ANYTHING")])

        expect:
        permissive.operator()
    }

    def "blank role short-circuits even for unauthenticated principals (endpoint auth is the chain's job)"() {
        given:
        GdprAuthzProperties.Roles blank = new GdprAuthzProperties.Roles("")
        GdprAuthzExpressions permissive = new GdprAuthzExpressions(blank)
        SecurityContextHolder.clearContext()

        expect:
        permissive.operator()
    }

    def "null roles fall back to DEFAULT (GDPR_OPERATOR)"() {
        given:
        GdprAuthzExpressions withNull = new GdprAuthzExpressions(null)
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
                "alice", "n/a",
                [new SimpleGrantedAuthority("GDPR_OPERATOR")])

        expect:
        withNull.operator()
    }
}
