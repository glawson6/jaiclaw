package io.jaiclaw.docstore;

import io.jaiclaw.docstore.analysis.DocStoreAnalyzer;
import io.jaiclaw.docstore.model.AddRequest;
import io.jaiclaw.docstore.model.DocStoreEntry;
import io.jaiclaw.docstore.repository.DocStoreRepository;
import io.jaiclaw.docstore.search.DocStoreSearchOptions;
import io.jaiclaw.docstore.search.DocStoreSearchProvider;
import io.jaiclaw.docstore.search.DocStoreSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Central service orchestrating DocStore operations: add, search, list, tag, analyze, delete.
 */
public class DocStoreService {

    private static final Logger log = LoggerFactory.getLogger(DocStoreService.class);

    private final DocStoreRepository repository;
    private final DocStoreSearchProvider searchProvider;
    private final DocStoreAnalyzer analyzer;

    public DocStoreService(DocStoreRepository repository, DocStoreSearchProvider searchProvider,
                           DocStoreAnalyzer analyzer) {
        this.repository = repository;
        this.searchProvider = searchProvider;
        this.analyzer = analyzer;
    }

    public DocStoreEntry add(AddRequest request) {
        String id = generateId();

        // Auto-tag by mime type
        Set<String> tags = new LinkedHashSet<>(request.tags());
        if (request.mimeType() != null) {
            tags.add(mimeTypeToTag(request.mimeType()));
        }
        if (request.entryType() == DocStoreEntry.EntryType.URL) {
            tags.add("url");
        }

        var entry = new DocStoreEntry(
                id,
                request.entryType(),
                request.filename(),
                request.mimeType(),
                request.fileSize(),
                request.sourceUrl(),
                request.channelId(),
                request.channelFileRef(),
                request.channelMessageRef(),
                request.userId(),
                request.chatId(),
                Instant.now(),
                tags,
                request.description(),
                null,
                null,
                null
        );

        repository.save(entry);
        searchProvider.index(entry);

        log.info("DocStore: indexed {} (id={}, type={}, user={})",
                entry.displayName(), id, request.entryType(), request.userId());

        return entry;
    }

    public DocStoreEntry addUrl(String url, String userId, String chatId, String channelId) {
        return add(new AddRequest(
                null, null, 0, null,
                channelId, null, null,
                userId, chatId, url,
                DocStoreEntry.EntryType.URL,
                Set.of(), null
        ));
    }

    public Optional<DocStoreEntry> get(String id) {
        return repository.findById(id);
    }

    public void delete(String id) {
        repository.deleteById(id);
        searchProvider.remove(id);
        log.info("DocStore: deleted entry {}", id);
    }

    public DocStoreEntry tag(String id, Set<String> newTags) {
        var updated = repository.update(id, entry -> {
            var merged = new LinkedHashSet<>(entry.tags());
            merged.addAll(newTags);
            return entry.withTags(merged);
        });
        if (updated != null) searchProvider.index(updated);
        return updated;
    }

    public DocStoreEntry describe(String id, String description) {
        var updated = repository.update(id, entry -> entry.withDescription(description));
        if (updated != null) searchProvider.index(updated);
        return updated;
    }

    public DocStoreEntry analyze(String id, byte[] content) {
        if (analyzer == null) {
            log.warn("No analyzer configured, skipping analysis for {}", id);
            return repository.findById(id).orElse(null);
        }

        var entry = repository.findById(id).orElse(null);
        if (entry == null) return null;

        if (content != null && entry.mimeType() != null && analyzer.supports(entry.mimeType())) {
            var result = analyzer.analyze(content, entry.mimeType(), entry.filename());
            var updated = repository.update(id, e -> e.withAnalysis(result));
            if (updated != null) searchProvider.index(updated);
            return updated;
        }

        return entry;
    }

    public List<DocStoreSearchResult> search(String query, DocStoreSearchOptions options) {
        return searchProvider.search(query, options);
    }

    public List<DocStoreEntry> list(String scopeId, int limit, int offset) {
        return repository.findRecent(scopeId, limit);
    }

    public List<DocStoreEntry> listByType(String mimePrefix, String scopeId, int limit) {
        return repository.findByMimeTypePrefix(mimePrefix, scopeId);
    }

    public List<DocStoreEntry> listByTags(Set<String> tags, String scopeId) {
        return repository.findByTags(tags, scopeId);
    }

    public long count(String scopeId) {
        return repository.count(scopeId);
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String mimeTypeToTag(String mimeType) {
        if (mimeType.startsWith("image/")) return "image";
        if (mimeType.startsWith("video/")) return "video";
        if (mimeType.startsWith("audio/")) return "audio";
        if (mimeType.contains("pdf")) return "pdf";
        if (mimeType.contains("spreadsheet") || mimeType.contains("excel")) return "spreadsheet";
        if (mimeType.contains("document") || mimeType.contains("word")) return "document";
        if (mimeType.contains("text/")) return "text";
        return "file";
    }
}
