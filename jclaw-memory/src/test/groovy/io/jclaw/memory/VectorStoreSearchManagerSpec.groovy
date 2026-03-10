package io.jclaw.memory

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import spock.lang.Specification

class VectorStoreSearchManagerSpec extends Specification {

    VectorStore vectorStore = Mock()
    VectorStoreSearchManager manager = new VectorStoreSearchManager(vectorStore)

    def "search returns empty for blank query"() {
        expect:
        manager.search("").isEmpty()
        manager.search("  ").isEmpty()
        manager.search(null).isEmpty()
    }

    def "search delegates to vector store"() {
        given:
        def doc = new Document("id1", "Matching content here", Map.of(
                "path", "file.txt",
                "source", "MEMORY"
        ))
        // Use reflection or builder to set score — Document constructor doesn't accept score
        vectorStore.similaritySearch(_ as SearchRequest) >> [doc]

        when:
        def results = manager.search("matching content")

        then:
        results.size() == 1
        results[0].path() == "file.txt"
        results[0].source() == MemorySource.MEMORY
        results[0].snippet().contains("Matching content")
    }

    def "search passes options to SearchRequest"() {
        given:
        def options = new MemorySearchOptions(5, 0.7, null)
        vectorStore.similaritySearch({ SearchRequest req ->
            req.topK == 5 && req.similarityThreshold == 0.7
        } as SearchRequest) >> []

        when:
        def results = manager.search("test query", options)

        then:
        1 * vectorStore.similaritySearch({ SearchRequest req ->
            req.topK == 5 && req.similarityThreshold == 0.7
        }) >> []
        results.isEmpty()
    }

    def "search returns empty when vector store returns empty"() {
        given:
        vectorStore.similaritySearch(_ as SearchRequest) >> []

        expect:
        manager.search("anything").isEmpty()
    }

    def "search maps SESSIONS source correctly"() {
        given:
        def doc = new Document("id2", "Session content", Map.of(
                "path", "session.txt",
                "source", "SESSIONS"
        ))
        vectorStore.similaritySearch(_ as SearchRequest) >> [doc]

        when:
        def results = manager.search("session")

        then:
        results[0].source() == MemorySource.SESSIONS
    }

    def "search handles unknown source gracefully"() {
        given:
        def doc = new Document("id3", "Content", Map.of(
                "path", "unknown.txt",
                "source", "INVALID_SOURCE"
        ))
        vectorStore.similaritySearch(_ as SearchRequest) >> [doc]

        when:
        def results = manager.search("content")

        then:
        results[0].source() == MemorySource.MEMORY
    }

    def "search truncates long snippets"() {
        given:
        def longText = "x" * 300
        def doc = new Document("id4", longText, Map.of("path", "long.txt", "source", "MEMORY"))
        vectorStore.similaritySearch(_ as SearchRequest) >> [doc]

        when:
        def results = manager.search("test")

        then:
        results[0].snippet().length() <= 203  // 200 + "..."
    }

    def "addDocument delegates to vector store"() {
        when:
        manager.addDocument("test.txt", "test content", MemorySource.MEMORY)

        then:
        1 * vectorStore.add({ List<Document> docs ->
            docs.size() == 1 &&
            docs[0].getText() == "test content" &&
            docs[0].getMetadata().get("path") == "test.txt" &&
            docs[0].getMetadata().get("source") == "MEMORY"
        })
    }

    def "deleteDocuments delegates to vector store"() {
        when:
        manager.deleteDocuments(["id1", "id2"])

        then:
        1 * vectorStore.delete(["id1", "id2"])
    }
}
