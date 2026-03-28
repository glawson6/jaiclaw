package io.jaiclaw.docstore.search;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.docstore.model.DocStoreEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Semantic/vector search implementation backed by Spring AI's {@link VectorStore}.
 * Embeds document text on index, performs similarity search on query.
 */
public class VectorDocStoreSearch implements DocStoreSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(VectorDocStoreSearch.class);
    private static final String ENTRY_ID_KEY = "docstore_entry_id";
    private static final int MAX_TEXT_LENGTH = 30000;

    private static final String TENANT_ID_KEY = "tenantId";

    private final VectorStore vectorStore;
    private final TenantGuard tenantGuard;
    private final Map<String, DocStoreEntry> entries = new ConcurrentHashMap<>();

    public VectorDocStoreSearch(VectorStore vectorStore) {
        this(vectorStore, null);
    }

    public VectorDocStoreSearch(VectorStore vectorStore, TenantGuard tenantGuard) {
        this.vectorStore = vectorStore;
        this.tenantGuard = tenantGuard;
    }

    @Override
    public List<DocStoreSearchResult> search(String query, DocStoreSearchOptions options) {
        if (query == null || query.isBlank()) return List.of();

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(options.maxResults())
                .similarityThreshold(0.0);

        // Build filter expression combining tenant and scope filters
        FilterExpressionBuilder fb = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op tenantFilter = null;
        FilterExpressionBuilder.Op scopeFilter = null;

        // Tenant filter in MULTI mode
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            tenantFilter = fb.eq(TENANT_ID_KEY, tenantId);
        }

        // Scope filter by userId or chatId if provided
        if (options.scopeId() != null) {
            scopeFilter = fb.or(
                    fb.eq("userId", options.scopeId()),
                    fb.eq("chatId", options.scopeId())
            );
        }

        if (tenantFilter != null && scopeFilter != null) {
            builder.filterExpression(fb.and(tenantFilter, scopeFilter).build());
        } else if (tenantFilter != null) {
            builder.filterExpression(tenantFilter.build());
        } else if (scopeFilter != null) {
            builder.filterExpression(scopeFilter.build());
        }

        List<Document> docs = vectorStore.similaritySearch(builder.build());
        log.debug("Vector search for '{}' returned {} results", query, docs.size());

        return docs.stream()
                .map(doc -> toSearchResult(doc, options))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(r -> matchesOptions(r.entry(), options))
                .limit(options.maxResults())
                .toList();
    }

    @Override
    public void index(DocStoreEntry entry) {
        entries.put(entry.id(), entry);

        String text = buildIndexText(entry);
        if (text.isBlank()) {
            log.debug("No indexable text for entry {}", entry.shortId());
            return;
        }

        // Truncate if needed
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ENTRY_ID_KEY, entry.id());
        metadata.put("userId", entry.userId() != null ? entry.userId() : "");
        metadata.put("chatId", entry.chatId() != null ? entry.chatId() : "");
        metadata.put("mimeType", entry.mimeType() != null ? entry.mimeType() : "");
        metadata.put("entryType", entry.entryType().name());

        // Tag with tenant for isolation in MULTI mode
        if (entry.tenantId() != null) {
            metadata.put(TENANT_ID_KEY, entry.tenantId());
        } else if (tenantGuard != null) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (tenantId != null) {
                metadata.put(TENANT_ID_KEY, tenantId);
            }
        }

        Document doc = new Document(entry.id(), text, metadata);
        vectorStore.add(List.of(doc));
        log.debug("Indexed entry {} in vector store", entry.shortId());
    }

    @Override
    public void remove(String entryId) {
        entries.remove(entryId);
        vectorStore.delete(List.of(entryId));
    }

    private String buildIndexText(DocStoreEntry entry) {
        var sb = new StringBuilder();
        if (entry.filename() != null) sb.append(entry.filename()).append("\n");
        if (entry.description() != null) sb.append(entry.description()).append("\n");
        if (entry.category() != null) sb.append(entry.category()).append("\n");
        if (entry.tags() != null && !entry.tags().isEmpty()) {
            sb.append(String.join(" ", entry.tags())).append("\n");
        }
        if (entry.analysis() != null) {
            if (entry.analysis().summary() != null) sb.append(entry.analysis().summary()).append("\n");
            if (entry.analysis().extractedText() != null) sb.append(entry.analysis().extractedText()).append("\n");
            entry.analysis().topics().forEach(t -> sb.append(t).append(" "));
            entry.analysis().entities().forEach(e -> sb.append(e).append(" "));
        }
        if (entry.sourceUrl() != null) sb.append(entry.sourceUrl()).append("\n");
        return sb.toString().strip();
    }

    private Optional<DocStoreSearchResult> toSearchResult(Document doc, DocStoreSearchOptions options) {
        String entryId = String.valueOf(doc.getMetadata().getOrDefault(ENTRY_ID_KEY, ""));
        DocStoreEntry entry = entries.get(entryId);
        if (entry == null) return Optional.empty();

        double score = doc.getScore() != null ? doc.getScore() : 0.0;
        String snippet = doc.getText();
        if (snippet != null && snippet.length() > 150) {
            snippet = snippet.substring(0, 150) + "...";
        }

        return Optional.of(new DocStoreSearchResult(entry, score, snippet != null ? snippet : ""));
    }

    private boolean matchesOptions(DocStoreEntry entry, DocStoreSearchOptions options) {
        // Tenant filtering in MULTI mode (defense-in-depth — vector filter should already handle this)
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (!tenantId.equals(entry.tenantId())) return false;
        }
        if (options.filterTags() != null && !options.filterTags().isEmpty() &&
                (entry.tags() == null || Collections.disjoint(entry.tags(), options.filterTags()))) {
            return false;
        }
        if (options.filterMimeType() != null &&
                (entry.mimeType() == null || !entry.mimeType().startsWith(options.filterMimeType()))) {
            return false;
        }
        if (options.after() != null && entry.indexedAt().isBefore(options.after())) return false;
        if (options.before() != null && entry.indexedAt().isAfter(options.before())) return false;
        return true;
    }
}
