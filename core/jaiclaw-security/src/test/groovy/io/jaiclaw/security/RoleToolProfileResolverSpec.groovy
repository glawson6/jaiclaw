package io.jaiclaw.security

import io.jaiclaw.core.tool.ToolProfile
import spock.lang.Specification

class RoleToolProfileResolverSpec extends Specification {

    def resolver = new RoleToolProfileResolver(
            [admin: "FULL", coach: "CODING", athlete: "MESSAGING", viewer: "MINIMAL"],
            "MINIMAL"
    )

    def "resolves single role to mapped profile"() {
        expect:
        resolver.resolve(["admin"]) == ToolProfile.FULL
        resolver.resolve(["coach"]) == ToolProfile.CODING
        resolver.resolve(["athlete"]) == ToolProfile.MESSAGING
        resolver.resolve(["viewer"]) == ToolProfile.MINIMAL
    }

    def "highest privilege wins when user has multiple roles"() {
        expect:
        resolver.resolve(["viewer", "admin"]) == ToolProfile.FULL
        // CODING outranks MESSAGING on the privilege lattice (shell and filesystem
        // access is wider than sending a message). Before 1.2.0 this asserted
        // MESSAGING, because ranking used ordinal() — declaration order — which
        // disagrees with ToolProfile.privilege(). The resolver, not the test, was wrong.
        resolver.resolve(["athlete", "coach"]) == ToolProfile.CODING
        resolver.resolve(["viewer", "coach"]) == ToolProfile.CODING
    }

    def "ranks by privilege lattice, not declaration order"() {
        expect:
        resolver.resolve(["coach", "athlete"]) == ToolProfile.CODING
        resolver.resolve(["athlete", "viewer"]) == ToolProfile.MESSAGING
    }

    def "falls back to default when no role matches"() {
        expect:
        resolver.resolve(["unknown_role"]) == ToolProfile.MINIMAL
    }

    def "returns default for null roles"() {
        expect:
        resolver.resolve(null) == ToolProfile.MINIMAL
    }

    def "returns default for empty roles"() {
        expect:
        resolver.resolve([]) == ToolProfile.MINIMAL
    }

    def "custom default profile is used"() {
        given:
        def customResolver = new RoleToolProfileResolver([:], "FULL")

        expect:
        customResolver.resolve([]) == ToolProfile.FULL
        customResolver.resolve(["unknown"]) == ToolProfile.FULL
    }

    def "role mapping is case-insensitive for profile values"() {
        given:
        def mixedCaseResolver = new RoleToolProfileResolver(
                [admin: "full", user: "Coding"],
                "minimal"
        )

        expect:
        mixedCaseResolver.resolve(["admin"]) == ToolProfile.FULL
        mixedCaseResolver.resolve(["user"]) == ToolProfile.CODING
    }
}
