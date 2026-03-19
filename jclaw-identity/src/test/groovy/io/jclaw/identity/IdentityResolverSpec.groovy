package io.jclaw.identity

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class IdentityResolverSpec extends Specification {

    @TempDir
    Path tempDir

    IdentityLinkStore store
    IdentityResolver resolver

    def setup() {
        store = new IdentityLinkStore(tempDir.resolve("identity-links.json"))
        resolver = new IdentityResolver(store)
    }

    def "resolve returns canonical ID when linked"() {
        given:
        store.link("canonical-1", "telegram", "123456")

        expect:
        resolver.resolve("telegram", "123456") == "canonical-1"
    }

    def "resolve returns channel user ID when not linked"() {
        expect:
        resolver.resolve("telegram", "999999") == "999999"
    }

    def "isLinked returns true for linked users"() {
        given:
        store.link("canonical-1", "slack", "U123")

        expect:
        resolver.isLinked("slack", "U123")
        !resolver.isLinked("slack", "U999")
    }
}
