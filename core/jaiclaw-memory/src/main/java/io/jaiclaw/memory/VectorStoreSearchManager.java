package io.jaiclaw.memory;

import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MemorySearchManager implementation backed by Spring AI's VectorStore.
 * All operations are partitioned by the current tenant via {@link TenantContextHolder}.
 */
public class VectorStoreSearchManager implements MemorySearchManager {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreSearchManager.class);
    private static final String TENANT_ID_KEY = "tenantId";

    private final VectorStore vectorStore;
    private final TenantGuard tenantGuard;

    public VectorStoreSearchManager(VectorStore vectorStore) {
        this(vectorStore, null);
    }

    public VectorStoreSearchManager(VectorStore vectorStore, TenantGuard tenantGuard) {
        this.vectorStore = vectorStore;
        this.tenantGuard = tenantGuard;
    }

    @Override
    public List<MemorySearchResult> search(String query, MemorySearchOptions options) {
        if (query == null || query.isBlank()) return List.of();

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(options.maxResults())
                .similarityThreshold(options.minScore());

        // Apply tenant filter — in MULTI mode this is mandatory (fail-closed)
        String tenantId = resolveTenantId();
        if (tenantId != null) {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            builder.filterExpression(fb.eq(TENANT_ID_KEY, tenantId).build());
        } else if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            // Fail-closed: MULTI mode requires a tenant filter
            return List.of();
        }

        List<Document> docs = vectorStore.similaritySearch(builder.build());
        log.debug("Vector search for '{}' (tenant={}) returned {} results",
                query, tenantId, docs.size());

        return docs.stream()
                .map(VectorStoreSearchManager::toResult)
                .toList();
    }

    /**
     * Add a document to the vector store for future similarity searches.
     * Automatically tags the document with the current tenant's ID.
     */
    public void addDocument(String path, String content, MemorySource source) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("path", path);
        metadata.put("source", source.name());

        String tenantId = resolveTenantId();
        if (tenantId != null) {
            metadata.put(TENANT_ID_KEY, tenantId);
        } else if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            // Fail-closed: MULTI mode requires tenant context for document indexing
            log.warn("Skipping document indexing — no tenant context in MULTI mode: {}", path);
            return;
        }

        Document doc = new Document(content, metadata);
        vectorStore.add(List.of(doc));
        log.debug("Added document to vector store: {} (tenant={})", path, tenantId);
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

    private String resolveTenantId() {
        return tenantGuard != null ? tenantGuard.requireTenantIfMulti() : null;
    }
}
