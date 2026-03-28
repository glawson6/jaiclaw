package io.jaiclaw.docstore.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.docstore.model.DocStoreEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * JSON file-backed DocStore repository. Loads from disk on startup,
 * flushes to disk on every write operation.
 */
public class JsonFileDocStoreRepository implements DocStoreRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonFileDocStoreRepository.class);

    private final Path storePath;
    private final ObjectMapper mapper;
    private final Map<String, DocStoreEntry> entries = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;

    public JsonFileDocStoreRepository(Path storagePath) {
        this(storagePath, null);
    }

    public JsonFileDocStoreRepository(Path storagePath, TenantGuard tenantGuard) {
        this.storePath = storagePath.resolve("docstore.json");
        this.tenantGuard = tenantGuard;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        loadFromDisk();
    }

    private Stream<DocStoreEntry> tenantFiltered() {
        Stream<DocStoreEntry> stream = entries.values().stream();
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            stream = stream.filter(e -> tenantId.equals(e.tenantId()));
        }
        return stream;
    }

    @Override
    public void save(DocStoreEntry entry) {
        entries.put(entry.id(), entry);
        flushToDisk();
    }

    @Override
    public Optional<DocStoreEntry> findById(String id) {
        DocStoreEntry entry = entries.get(id);
        if (entry != null && tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (!tenantId.equals(entry.tenantId())) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(entry);
    }

    @Override
    public void deleteById(String id) {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            DocStoreEntry entry = entries.get(id);
            if (entry == null) return;
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (!tenantId.equals(entry.tenantId())) return;
        }
        entries.remove(id);
        flushToDisk();
    }

    @Override
    public DocStoreEntry update(String id, UnaryOperator<DocStoreEntry> mutator) {
        DocStoreEntry updated;
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            updated = entries.computeIfPresent(id, (k, v) -> {
                if (!tenantId.equals(v.tenantId())) return v; // no-op for wrong tenant
                return mutator.apply(v);
            });
        } else {
            updated = entries.computeIfPresent(id, (k, v) -> mutator.apply(v));
        }
        if (updated != null) flushToDisk();
        return updated;
    }

    @Override
    public List<DocStoreEntry> findByUserId(String userId, int limit, int offset) {
        return tenantFiltered()
                .filter(e -> userId.equals(e.userId()))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<DocStoreEntry> findByChatId(String chatId, int limit, int offset) {
        return tenantFiltered()
                .filter(e -> chatId.equals(e.chatId()))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<DocStoreEntry> findByTags(Set<String> tags, String scopeId) {
        return tenantFiltered()
                .filter(e -> matchesScope(e, scopeId))
                .filter(e -> e.tags() != null && !Collections.disjoint(e.tags(), tags))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .toList();
    }

    @Override
    public List<DocStoreEntry> findByMimeTypePrefix(String mimeTypePrefix, String scopeId) {
        return tenantFiltered()
                .filter(e -> matchesScope(e, scopeId))
                .filter(e -> e.mimeType() != null && e.mimeType().startsWith(mimeTypePrefix))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .toList();
    }

    @Override
    public List<DocStoreEntry> findRecent(String scopeId, int limit) {
        return tenantFiltered()
                .filter(e -> matchesScope(e, scopeId))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public long count(String scopeId) {
        return tenantFiltered()
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
