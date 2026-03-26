package io.jaiclaw.docstore.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jaiclaw.docstore.model.DocStoreEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * JSON file-backed DocStore repository. Loads from disk on startup,
 * flushes to disk on every write operation.
 */
public class JsonFileDocStoreRepository implements DocStoreRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonFileDocStoreRepository.class);

    private final Path storePath;
    private final ObjectMapper mapper;
    private final Map<String, DocStoreEntry> entries = new ConcurrentHashMap<>();

    public JsonFileDocStoreRepository(Path storagePath) {
        this.storePath = storagePath.resolve("docstore.json");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        loadFromDisk();
    }

    @Override
    public void save(DocStoreEntry entry) {
        entries.put(entry.id(), entry);
        flushToDisk();
    }

    @Override
    public Optional<DocStoreEntry> findById(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public void deleteById(String id) {
        entries.remove(id);
        flushToDisk();
    }

    @Override
    public DocStoreEntry update(String id, UnaryOperator<DocStoreEntry> mutator) {
        var updated = entries.computeIfPresent(id, (k, v) -> mutator.apply(v));
        if (updated != null) flushToDisk();
        return updated;
    }

    @Override
    public List<DocStoreEntry> findByUserId(String userId, int limit, int offset) {
        return entries.values().stream()
                .filter(e -> userId.equals(e.userId()))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<DocStoreEntry> findByChatId(String chatId, int limit, int offset) {
        return entries.values().stream()
                .filter(e -> chatId.equals(e.chatId()))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<DocStoreEntry> findByTags(Set<String> tags, String scopeId) {
        return entries.values().stream()
                .filter(e -> matchesScope(e, scopeId))
                .filter(e -> e.tags() != null && !Collections.disjoint(e.tags(), tags))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .toList();
    }

    @Override
    public List<DocStoreEntry> findByMimeTypePrefix(String mimeTypePrefix, String scopeId) {
        return entries.values().stream()
                .filter(e -> matchesScope(e, scopeId))
                .filter(e -> e.mimeType() != null && e.mimeType().startsWith(mimeTypePrefix))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .toList();
    }

    @Override
    public List<DocStoreEntry> findRecent(String scopeId, int limit) {
        return entries.values().stream()
                .filter(e -> matchesScope(e, scopeId))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public long count(String scopeId) {
        return entries.values().stream()
                .filter(e -> matchesScope(e, scopeId))
                .count();
    }

    private boolean matchesScope(DocStoreEntry entry, String scopeId) {
        if (scopeId == null) return true;
        return scopeId.equals(entry.userId()) || scopeId.equals(entry.chatId());
    }

    private void loadFromDisk() {
        if (!Files.exists(storePath)) return;
        try {
            List<DocStoreEntry> loaded = mapper.readValue(storePath.toFile(),
                    new TypeReference<List<DocStoreEntry>>() {});
            loaded.forEach(e -> entries.put(e.id(), e));
            log.info("Loaded {} DocStore entries from {}", entries.size(), storePath);
        } catch (IOException e) {
            log.warn("Failed to load DocStore from {}: {}", storePath, e.getMessage());
        }
    }

    private void flushToDisk() {
        try {
            Files.createDirectories(storePath.getParent());
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(storePath.toFile(), List.copyOf(entries.values()));
        } catch (IOException e) {
            log.error("Failed to flush DocStore to {}: {}", storePath, e.getMessage());
        }
    }
}
