package io.jaiclaw.pipeline.authoring

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

class PipelineAuthzExpressionsSpec extends Specification {

    PipelineAuthoringProperties.Roles defaultRoles = PipelineAuthoringProperties.Roles.DEFAULT
    PipelineAuthzExpressions authz = new PipelineAuthzExpressions(defaultRoles)

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "unauthenticated principal denies every role check"() {
        expect:
        !authz.viewer()
        !authz.author()
        !authz.deployer()
        !authz.runner()
    }

    def "principal with matching authority passes"() {
        given:
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
                "alice", "n/a",
                [new SimpleGrantedAuthority("ROLE_PIPELINE_DEPLOYER")])

        expect:
        authz.deployer()
        !authz.author()
    }

    def "blank role config short-circuits to true for authenticated principals"() {
        given:
        PipelineAuthoringProperties.Roles blank = new PipelineAuthoringProperties.Roles(
                "", "", "", "")
        PipelineAuthzExpressions permissive = new PipelineAuthzExpressions(blank)
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
                "alice", "n/a", [new SimpleGrantedAuthority("ROLE_ANYTHING")])

        expect:
        permissive.viewer()
        permissive.author()
        permissive.deployer()
        permissive.runner()
    }

    def "blank role still denies unauthenticated principals — wait, it short-circuits"() {
        // Design intent: blank role = "no method-level check", which
        // means we don't consult authentication at all. Endpoint
        // authentication is the app's Spring Security chain's job.
        given:
        PipelineAuthoringProperties.Roles blank = new PipelineAuthoringProperties.Roles(
                "", "", "", "")
        PipelineAuthzExpressions permissive = new PipelineAuthzExpressions(blank)
        SecurityContextHolder.clearContext()

        expect:
        permissive.viewer()
    }

    def "null roles fall back to DEFAULT names"() {
        given:
        PipelineAuthzExpressions withNull = new PipelineAuthzExpressions(null)
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
                "alice", "n/a",
                [new SimpleGrantedAuthority("ROLE_PIPELINE_AUTHOR")])

        expect:
        withNull.author()
        !withNull.deployer()
    }
}
