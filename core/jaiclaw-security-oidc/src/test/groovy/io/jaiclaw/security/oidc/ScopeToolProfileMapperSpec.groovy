package io.jaiclaw.security.oidc

import io.jaiclaw.core.tool.ToolProfile
import spock.lang.Specification

class ScopeToolProfileMapperSpec extends Specification {

    def mapper = new ScopeToolProfileMapper([
            "jaiclaw:tools:minimal"  : "MINIMAL",
            "jaiclaw:tools:messaging": "MESSAGING",
            "jaiclaw:tools:coding"   : "CODING",
            "jaiclaw:tools:full"     : "FULL",
    ])

    def "maps a single scope"() {
        expect:
        mapper.map(["jaiclaw:tools:coding"]) == ToolProfile.CODING
    }

    def "highest privilege wins, ranked by the lattice not declaration order"() {
        expect: "CODING outranks MESSAGING on privilege(); ordinal() would disagree"
        mapper.map(["jaiclaw:tools:messaging", "jaiclaw:tools:coding"]) == ToolProfile.CODING
        mapper.map(["jaiclaw:tools:coding", "jaiclaw:tools:messaging"]) == ToolProfile.CODING
        mapper.map(["jaiclaw:tools:minimal", "jaiclaw:tools:full"]) == ToolProfile.FULL
    }

    def "returns null when nothing matches, so the caller can apply its own default"() {
        expect:
        mapper.map(["openid", "profile"]) == null
        mapper.map([]) == null
        mapper.map(null) == null
        new ScopeToolProfileMapper([:]).map(["jaiclaw:tools:full"]) == null
    }

    def "an invalid profile name fails at startup, not at request time"() {
        when:
        new ScopeToolProfileMapper(["x": "SUPERUSER"])

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("SUPERUSER")
        e.message.contains("Valid profiles")
    }

    def "profile names are case-insensitive"() {
        expect:
        new ScopeToolProfileMapper(["x": "coding"]).map(["x"]) == ToolProfile.CODING
    }

    def "isEmpty reports whether any mapping is configured"() {
        expect:
        !mapper.isEmpty()
        new ScopeToolProfileMapper(null).isEmpty()
    }
}
