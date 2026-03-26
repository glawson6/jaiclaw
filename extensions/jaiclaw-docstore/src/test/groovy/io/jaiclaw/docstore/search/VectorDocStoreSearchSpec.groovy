package io.jaiclaw.docstore.search

import io.jaiclaw.docstore.model.AnalysisResult
import io.jaiclaw.docstore.model.DocStoreEntry
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import spock.lang.Specification

import java.time.Instant

class VectorDocStoreSearchSpec extends Specification {

    VectorStore vectorStore = Mock()
    VectorDocStoreSearch search = new VectorDocStoreSearch(vectorStore)

    def "index adds document to vector store with metadata"() {
        given:
        def entry = makeEntry("e1", "report.pdf", "Quarterly earnings report")

        when:
        search.index(entry)

        then:
        1 * vectorStore.add(_) >> { List args ->
            List<Document> docs = args[0]
            def doc = docs[0]
            def text = doc.getText()
            assert docs.size() == 1
            assert doc.id == "e1"
            assert doc.getMetadata()["docstore_entry_id"] == "e1"
            assert doc.getMetadata()["userId"] == "user1"
            assert text.contains("report.pdf")
            assert text.contains("Quarterly earnings report")
        }
    }

    def "search returns results from vector store"() {
        given:
        def entry = makeEntry("e1", "report.pdf", "Quarterly earnings report")
        search.index(entry)

        and:
        def doc = new Document("e1", "report.pdf\nQuarterly earnings report",
                [docstore_entry_id: "e1", userId: "user1", chatId: "chat1"])
        vectorStore.similaritySearch(_) >> [doc]

        when:
        def results = search.search("earnings", DocStoreSearchOptions.DEFAULT)

        then:
        results.size() == 1
        results[0].entry().id() == "e1"
    }

    def "search returns empty for blank query"() {
        expect:
        search.search("", DocStoreSearchOptions.DEFAULT).isEmpty()
        search.search(null, DocStoreSearchOptions.DEFAULT).isEmpty()
        search.search("  ", DocStoreSearchOptions.DEFAULT).isEmpty()
    }

    def "remove deletes from vector store"() {
        when:
        search.remove("e1")

        then:
        1 * vectorStore.delete(["e1"])
    }

    def "search filters by tags"() {
        given:
        def entry = makeEntry("e1", "report.pdf", "desc", Set.of("finance"))
        search.index(entry)

        def doc = new Document("e1", "text", [docstore_entry_id: "e1"])
        vectorStore.similaritySearch(_) >> [doc]

        when: "filtering for a tag that matches"
        def options = new DocStoreSearchOptions(null, 10, Set.of("finance"), null, null, null)
        def results = search.search("report", options)

        then:
        results.size() == 1

        when: "filtering for a tag that doesn't match"
        def options2 = new DocStoreSearchOptions(null, 10, Set.of("legal"), null, null, null)
        def results2 = search.search("report", options2)

        then:
        results2.isEmpty()
    }

    def "index includes analysis text when available"() {
        given:
        def analysis = new AnalysisResult("Summary of doc", "Full extracted text",
                ["finance", "quarterly"], ["Acme Corp"], [:], AnalysisResult.AnalysisLevel.LLM)
        def entry = new DocStoreEntry("e1", DocStoreEntry.EntryType.FILE, "report.pdf",
                "application/pdf", 1000L, null, "telegram", null, null,
                "user1", "chat1", Instant.now(), Set.of(), "A report", null, analysis)

        when:
        search.index(entry)

        then:
        1 * vectorStore.add(_) >> { List args ->
            List<Document> docs = args[0]
            def text = docs[0].getText()
            assert text.contains("Summary of doc")
            assert text.contains("Full extracted text")
            assert text.contains("finance")
            assert text.contains("Acme Corp")
        }
    }

    private DocStoreEntry makeEntry(String id, String filename, String description,
                                     Set<String> tags = Set.of()) {
        new DocStoreEntry(id, DocStoreEntry.EntryType.FILE, filename,
                "application/pdf", 1000L, null, "telegram", null, null,
                "user1", "chat1", Instant.now(), tags, description, null, null)
    }
}
