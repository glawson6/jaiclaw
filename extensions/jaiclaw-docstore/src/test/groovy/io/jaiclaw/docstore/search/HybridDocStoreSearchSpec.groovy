package io.jaiclaw.docstore.search

import io.jaiclaw.docstore.model.DocStoreEntry
import spock.lang.Specification

import java.time.Instant

class HybridDocStoreSearchSpec extends Specification {

    FullTextDocStoreSearch fullText = Mock()
    VectorDocStoreSearch vector = Mock()
    HybridDocStoreSearch hybrid = new HybridDocStoreSearch(fullText, vector, 0.3, 0.7)

    def "search merges results from both providers using RRF"() {
        given:
        def entry1 = makeEntry("e1", "Budget Report")
        def entry2 = makeEntry("e2", "Financial Summary")
        def entry3 = makeEntry("e3", "Team Meeting Notes")

        // Full-text: e1 rank 1, e3 rank 2
        fullText.search(_, _) >> [
                new DocStoreSearchResult(entry1, 1.0, "budget"),
                new DocStoreSearchResult(entry3, 0.5, "notes")
        ]

        // Vector: e2 rank 1, e1 rank 2
        vector.search(_, _) >> [
                new DocStoreSearchResult(entry2, 0.9, "financial"),
                new DocStoreSearchResult(entry1, 0.7, "budget")
        ]

        when:
        def results = hybrid.search("budget report", DocStoreSearchOptions.DEFAULT)

        then:
        results.size() == 3
        // e1 appears in both lists so should score highest (RRF from both)
        results[0].entry().id() == "e1"
        // e2 appears only in vector (weight 0.7) at rank 1
        // e3 appears only in fullText (weight 0.3) at rank 2
        results.collect { it.entry().id() } as Set == ["e1", "e2", "e3"] as Set
    }

    def "search returns empty for blank query"() {
        expect:
        hybrid.search("", DocStoreSearchOptions.DEFAULT).isEmpty()
        hybrid.search(null, DocStoreSearchOptions.DEFAULT).isEmpty()
    }

    def "search handles one provider returning empty"() {
        given:
        def entry = makeEntry("e1", "Report")

        fullText.search(_, _) >> [new DocStoreSearchResult(entry, 1.0, "snippet")]
        vector.search(_, _) >> []

        when:
        def results = hybrid.search("report", DocStoreSearchOptions.DEFAULT)

        then:
        results.size() == 1
        results[0].entry().id() == "e1"
    }

    def "search deduplicates entries appearing in both providers"() {
        given:
        def entry = makeEntry("e1", "Same Document")

        fullText.search(_, _) >> [new DocStoreSearchResult(entry, 1.0, "snippet")]
        vector.search(_, _) >> [new DocStoreSearchResult(entry, 0.9, "snippet")]

        when:
        def results = hybrid.search("document", DocStoreSearchOptions.DEFAULT)

        then:
        results.size() == 1
        results[0].entry().id() == "e1"
        // Score should be sum of both RRF contributions
        results[0].score() > 0
    }

    def "index delegates to both providers"() {
        given:
        def entry = makeEntry("e1", "Report")

        when:
        hybrid.index(entry)

        then:
        1 * fullText.index(entry)
        1 * vector.index(entry)
    }

    def "remove delegates to both providers"() {
        when:
        hybrid.remove("e1")

        then:
        1 * fullText.remove("e1")
        1 * vector.remove("e1")
    }

    def "search respects maxResults limit"() {
        given:
        def entries = (1..20).collect { makeEntry("e$it", "Document $it") }

        fullText.search(_, _) >> entries.collect { new DocStoreSearchResult(it, 0.5, "s") }
        vector.search(_, _) >> entries.reverse().collect { new DocStoreSearchResult(it, 0.5, "s") }

        when:
        def options = new DocStoreSearchOptions(null, 5, null, null, null, null)
        def results = hybrid.search("document", options)

        then:
        results.size() == 5
    }

    private DocStoreEntry makeEntry(String id, String filename) {
        new DocStoreEntry(id, DocStoreEntry.EntryType.FILE, filename,
                "application/pdf", 1000L, null, "telegram", null, null,
                "user1", "chat1", Instant.now(), Set.of(), null, null, null)
    }
}
