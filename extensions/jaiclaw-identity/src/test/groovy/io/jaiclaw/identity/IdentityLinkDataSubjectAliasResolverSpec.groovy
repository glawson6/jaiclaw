package io.jaiclaw.identity

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant

class IdentityLinkDataSubjectAliasResolverSpec extends Specification {

    @TempDir Path tempDir

    IdentityLinkStore store
    IdentityLinkDataSubjectAliasResolver resolver

    def setup() {
        store = new IdentityLinkStore(tempDir.resolve("links.json"))
        resolver = new IdentityLinkDataSubjectAliasResolver(store)
    }

    def "an unlinked subject resolves to itself — pre-1.4.0 behaviour preserved"() {
        expect:
        resolver.aliasesOf("acme", "12345") == ["12345"]
    }

    def "a verified subject expands across every linked channel"() {
        given: "the same human on Telegram and Slack"
        def now = Instant.now()
        store.link("subject-abc", "telegram", "12345", "https://idp.example.com", now)
        store.link("subject-abc", "slack", "U999", "https://idp.example.com", now)

        when: "an erasure request names only the Telegram id"
        def aliases = resolver.aliasesOf("acme", "12345")

        then: "it must reach the Slack data too, or Article 17 is not satisfied"
        aliases.contains("12345")
        aliases.contains("U999")
        aliases.contains("subject-abc")
    }

    def "resolves equally when the request names the canonical subject"() {
        given:
        def now = Instant.now()
        store.link("subject-abc", "telegram", "12345", "https://idp.example.com", now)
        store.link("subject-abc", "slack", "U999", "https://idp.example.com", now)

        expect:
        resolver.aliasesOf("acme", "subject-abc").containsAll(["12345", "U999", "subject-abc"])
    }

    def "ASSERTED links are not followed"() {
        given: "a link anyone could have created by calling link() directly"
        store.link("subject-abc", "telegram", "12345")
        store.link("subject-abc", "slack", "U999")

        when:
        def aliases = resolver.aliasesOf("acme", "12345")

        then: "otherwise a bogus link becomes a way to delete someone else's data"
        aliases == ["12345"]
    }

    def "the input is always included, so nothing is ever lost"() {
        expect: "an inclusive resolver can only over-approximate, never under"
        resolver.aliasesOf("acme", input).contains(input)

        where:
        input << ["12345", "subject-abc", "unknown-id"]
    }

    def "degrades safely without a store"() {
        expect:
        new IdentityLinkDataSubjectAliasResolver(null).aliasesOf("acme", "12345") == ["12345"]
    }

    def "a null or blank subject identifies nobody and yields no aliases"() {
        expect: "returning [null] would only push the problem into the erasure loop"
        resolver.aliasesOf("acme", null) == []
        resolver.aliasesOf("acme", "") == []
        resolver.aliasesOf("acme", "   ") == []
    }
}
