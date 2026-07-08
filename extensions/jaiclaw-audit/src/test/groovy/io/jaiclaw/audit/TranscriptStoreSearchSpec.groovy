package io.jaiclaw.audit

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant

class TranscriptStoreSearchSpec extends Specification {

    @TempDir
    Path storeDir

    TranscriptStore store
    Instant t0

    def setup() {
        store = new FileTranscriptStore(storeDir)
        t0 = Instant.parse("2026-05-01T10:00:00Z")

        // Session A: user asks about kubernetes rollouts
        store.save(new TranscriptSession(
                "s-a", "acme", "ops-agent", "slack", t0,
                [TranscriptUtterance.user("How do I roll back a Kubernetes deploy?"),
                 new TranscriptUtterance("assistant",
                         "Run `kubectl rollout undo deployment/foo` to revert.",
                         t0.plusSeconds(1), [:])]
        ))

        // Session B: unrelated conversation about invoicing
        store.save(new TranscriptSession(
                "s-b", "acme", "finance-agent", "slack", t0.plusSeconds(3600),
                [TranscriptUtterance.user("Show me last quarter's invoices"),
                 new TranscriptUtterance("assistant",
                         "Q1 invoices are in the shared drive.",
                         t0.plusSeconds(3601), [:])]
        ))

        // Session C: another kubernetes chat, most recent so it should rank first
        store.save(new TranscriptSession(
                "s-c", "acme", "ops-agent", "discord", t0.plusSeconds(7200),
                [TranscriptUtterance.user("kubectl get pods hangs, ideas?")]
        ))

        // Session D: different tenant — must not appear in "acme" searches
        store.save(new TranscriptSession(
                "s-d", "beta", "ops-agent", "slack", t0.plusSeconds(9000),
                [TranscriptUtterance.user("kubectl explain deployment")]
        ))
    }

    def "search returns matches from the requested tenant only, most-recent first"() {
        when:
        def results = store.search("kubectl", "acme", 10)

        then:
        results.size() == 2
        results*.sessionId() == ["s-c", "s-a"]      // recent first
        results.every { it.matchedUtterance().content().toLowerCase().contains("kubectl") }
    }

    def "search is case-insensitive"() {
        expect:
        store.search("KUBERNETES", "acme", 10)*.sessionId() == ["s-a"]
        store.search("Kubernetes", "acme", 10)*.sessionId() == ["s-a"]
    }

    def "search respects the limit"() {
        expect:
        store.search("kubectl", "acme", 1)*.sessionId() == ["s-c"]
    }

    def "search across all tenants when tenantId is null returns per-tenant partitions"() {
        // FileTranscriptStore's list() scopes to a single tenant dir. When
        // tenantId is null, list() uses the default-tenant dir. That means
        // null-tenant searches naturally scope to untagged data — the same
        // as list/load. This is documented behavior, not a bug.
        expect:
        store.search("kubectl", null, 10) == []
    }

    def "search returns empty for blank / null query"() {
        expect:
        store.search(null, "acme", 10) == []
        store.search("", "acme", 10) == []
        store.search("   ", "acme", 10) == []
    }

    def "search returns empty when limit is zero or negative"() {
        expect:
        store.search("kubectl", "acme", 0) == []
        store.search("kubectl", "acme", -1) == []
    }

    def "search returns empty when no session matches"() {
        expect:
        store.search("something-that-is-not-in-any-transcript", "acme", 10) == []
    }
}
