package io.jaiclaw.identity

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant

class CanonicalUserKeyResolverSpec extends Specification {

    @TempDir Path tempDir

    IdentityLinkStore store
    static final String LEGACY = CanonicalUserKeyResolver.legacyHash("telegram", "12345")

    def setup() {
        store = new IdentityLinkStore(tempDir.resolve("links.json"))
    }

    def "the legacy hash must stay byte-identical or every existing key is orphaned"() {
        expect: "16-char hex prefix of sha256(channelId:peerId) — the pre-1.4.0 shape"
        LEGACY.length() == 16
        LEGACY ==~ /[0-9a-f]{16}/
        CanonicalUserKeyResolver.legacyHash("telegram", "12345") == LEGACY
    }

    def "with the feature off, keys stay on the legacy hash even for a verified user"() {
        given:
        store.link("subject-abc", "telegram", "12345", "https://idp.example.com", Instant.now())
        def resolver = new CanonicalUserKeyResolver(store, false)

        expect: "the migration is opt-in, so nothing moves until an operator says so"
        resolver.resolveForWrite("telegram", "12345") == LEGACY
        resolver.resolveForRead("telegram", "12345") == [LEGACY]
        !resolver.hasCanonicalKey("telegram", "12345")
    }

    def "an unlinked user stays on the legacy hash"() {
        given:
        def resolver = new CanonicalUserKeyResolver(store, true)

        expect:
        resolver.resolveForWrite("telegram", "12345") == LEGACY
        resolver.resolveForRead("telegram", "12345") == [LEGACY]
    }

    def "an ASSERTED link is not enough to move a user's state"() {
        given: "a link created by calling link() directly — nothing proves the binding"
        store.link("claimed-subject", "telegram", "12345")
        def resolver = new CanonicalUserKeyResolver(store, true)

        expect: "otherwise anyone able to call link() could adopt another user's memory"
        resolver.resolveForWrite("telegram", "12345") == LEGACY
        !resolver.hasCanonicalKey("telegram", "12345")
    }

    def "a VERIFIED link moves writes to the canonical subject"() {
        given:
        store.link("subject-abc", "telegram", "12345", "https://idp.example.com", Instant.now())
        def resolver = new CanonicalUserKeyResolver(store, true)

        expect:
        resolver.resolveForWrite("telegram", "12345") == "subject-abc"
        resolver.hasCanonicalKey("telegram", "12345")
    }

    def "reads try canonical first, then fall back to the legacy hash"() {
        given:
        store.link("subject-abc", "telegram", "12345", "https://idp.example.com", Instant.now())
        def resolver = new CanonicalUserKeyResolver(store, true)

        when:
        def keys = resolver.resolveForRead("telegram", "12345")

        then: "pre-migration state stays reachable — no batch job, no window where reads fail"
        keys == ["subject-abc", LEGACY]
    }

    def "cross-channel continuity: two channels, one verified subject, one key"() {
        given:
        def verifiedAt = Instant.now()
        store.link("subject-abc", "telegram", "12345", "https://idp.example.com", verifiedAt)
        store.link("subject-abc", "slack", "U999", "https://idp.example.com", verifiedAt)
        def resolver = new CanonicalUserKeyResolver(store, true)

        expect: "the same human on two channels is finally one user"
        resolver.resolveForWrite("telegram", "12345") == "subject-abc"
        resolver.resolveForWrite("slack", "U999") == "subject-abc"

        and: "whereas the legacy hashes were unrelated"
        CanonicalUserKeyResolver.legacyHash("telegram", "12345") !=
                CanonicalUserKeyResolver.legacyHash("slack", "U999")
    }

    def "null or blank inputs yield no key rather than a bogus one"() {
        given:
        def resolver = new CanonicalUserKeyResolver(store, true)

        expect:
        CanonicalUserKeyResolver.legacyHash(channel, peer) == null
        resolver.resolveForRead(channel, peer) == []

        where:
        channel    | peer
        null       | "123"
        "telegram" | null
        ""         | "123"
        "telegram" | "  "
    }

    def "a missing link store degrades to the legacy hash rather than failing"() {
        given:
        def resolver = new CanonicalUserKeyResolver(null, true)

        expect:
        resolver.resolveForWrite("telegram", "12345") == LEGACY
    }
}
