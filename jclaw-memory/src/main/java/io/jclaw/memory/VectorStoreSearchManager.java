package io.jclaw.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

/**
 * MemorySearchManager implementation backed by Spring AI's VectorStore.
 * Delegates similarity search to the configured vector store and maps
 * results back to MemorySearchResult records.
 */
public class VectorStoreSearchManager implements MemorySearchManager {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreSearchManager.class);

    private final VectorStore vectorStore;

    public VectorStoreSearchManager(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<MemorySearchResult> search(String query, MemorySearchOptions options) {
        if (query == null || query.isBlank()) return List.of();

        var request = SearchRequest.builder()
                .query(query)
                .topK(options.maxResults())
                .similarityThreshold(options.minScore())
                .build();

        List<Document> docs = vectorStore.similaritySearch(request);
        log.debug("Vector search for '{}' returned {} results", query, docs.size());

        return docs.stream()
                .map(VectorStoreSearchManager::toResult)
                .toList();
    }

    /**
     * Add a document to the vector store for future similarity searches.
     */
    public void addDocument(String path, String content, MemorySource source) {
        var doc = new Document(content, Map.of(
                "path", path,
                "source", source.name()
        ));
        vectorStore.add(List.of(doc));
        log.debug("Added document to vector store: {}", path);
    }

    /**
     * Remove documents from the vector store by their IDs.
     */
    public void deleteDocuments(List<String> ids) {
        vectorStore.delete(ids);
    }

    private static MemorySearchResult toResult(Document doc) {
        String path = String.valueOf(doc.getMetadata().getOrDefault("path", ""));
        String sourceName = String.valueOf(doc.getMetadata().getOrDefault("source", "MEMORY"));
        MemorySource source;
        try {
            source = MemorySource.valueOf(sourceName);
        } catch (IllegalArgumentException e) {
            source = MemorySource.MEMORY;
        }

        double score = doc.getScore() != null ? doc.getScore() : 0.0;
        String snippet = doc.getText();
        if (snippet != null && snippet.length() > 200) {
            snippet = snippet.substring(0, 200) + "...";
        }

        return new MemorySearchResult(path, 0, 0, score, snippet != null ? snippet : "", source);
    }
}
