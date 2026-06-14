package io.jaiclaw.hermes.soul.user

import io.jaiclaw.identity.IdentityResolver
import spock.lang.Specification

class IdentityLinkUserKeyResolverSpec extends Specification {

    def "delegates to IdentityResolver when present"() {
        given:
        IdentityResolver delegate = Mock()
        IdentityLinkUserKeyResolver resolver = new IdentityLinkUserKeyResolver(delegate)

        when:
        String key = resolver.resolve("slack", "U12345")

        then:
        1 * delegate.resolve("slack", "U12345") >> "canonical-uuid-abc"
        key == "canonical-uuid-abc"
        resolver.linkedKeyAvailable
    }

    def "falls back to deterministic hash when IdentityResolver is null"() {
        given:
        IdentityLinkUserKeyResolver resolver = new IdentityLinkUserKeyResolver(null)

        when:
        String key = resolver.resolve("slack", "U12345")

        then:
        key.length() == 16
        key ==~ /[0-9a-f]{16}/
        !resolver.linkedKeyAvailable
    }

    def "deterministic hash is stable across calls (continuity invariant)"() {
        given:
        IdentityLinkUserKeyResolver r1 = new IdentityLinkUserKeyResolver(null)
        IdentityLinkUserKeyResolver r2 = new IdentityLinkUserKeyResolver(null)

        expect:
        r1.resolve("slack", "U99") == r2.resolve("slack", "U99")
    }

    def "different channels with the same peer id produce different keys"() {
        given:
        IdentityLinkUserKeyResolver r = new IdentityLinkUserKeyResolver(null)

        expect:
        r.resolve("slack", "U99") != r.resolve("telegram", "U99")
    }

    def "different peers on the same channel produce different keys"() {
        given:
        IdentityLinkUserKeyResolver r = new IdentityLinkUserKeyResolver(null)

        expect:
        r.resolve("slack", "alice") != r.resolve("slack", "bob")
    }

    def "null channel or peerId rejected"() {
        given:
        IdentityLinkUserKeyResolver r = new IdentityLinkUserKeyResolver(null)

        when:
        r.resolve(channel, peerId)

        then:
        thrown(NullPointerException)

        where:
        channel | peerId
        null    | "u"
        "slack" | null
    }
}
