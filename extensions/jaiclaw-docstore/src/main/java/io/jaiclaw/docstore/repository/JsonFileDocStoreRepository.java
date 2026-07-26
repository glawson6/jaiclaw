package io.jaiclaw.docstore.repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
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
        this(storagePath, new TenantGuard(TenantProperties.DEFAULT));
    }

    public JsonFileDocStoreRepository(Path storagePath, TenantGuard tenantGuard) {
        this.storePath = storagePath.resolve("docstore.json");
        this.tenantGuard = tenantGuard != null ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
        this.mapper = new ObjectMapper()
                
                ;
        loadFromDisk();
    }

    /** Current tenant id (default-tenant-id in SINGLE, throws in MULTI with no context). */
    private String currentTenantId() {
        if (tenantGuard.isMultiTenant()) {
            return tenantGuard.requireTenantIfMulti();
        }
        return tenantGuard.getProperties().defaultTenantId();
    }

    /** Storage key for the given entry. Uses the entry's own tenantId when present. */
    private String storageKey(DocStoreEntry entry) {
        String tenantId = entry.tenantId() != null ? entry.tenantId() : currentTenantId();
        return tenantId + ":" + entry.id();
    }

    /** Storage key for the given entry id, scoped to the current tenant. */
    private String storageKey(String entryId) {
        return currentTenantId() + ":" + entryId;
    }

    /** Stream of entries visible to the current tenant. */
    private Stream<DocStoreEntry> tenantFiltered() {
        String prefix = currentTenantId() + ":";
        return entries.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue);
    }

    @Override
    public void save(DocStoreEntry entry) {
        entries.put(storageKey(entry), entry);
        flushToDisk();
    }

    @Override
    public Optional<DocStoreEntry> findById(String id) {
        return Optional.ofNullable(entries.get(storageKey(id)));
    }

    @Override
    public void deleteById(String id) {
        if (entries.remove(storageKey(id)) != null) {
            flushToDisk();
        }
    }

    @Override
    public DocStoreEntry update(String id, UnaryOperator<DocStoreEntry> mutator) {
        DocStoreEntry updated = entries.computeIfPresent(storageKey(id), (k, v) -> mutator.apply(v));
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
            loaded.forEach(e -> entries.put(storageKey(e), e));
            log.info("Loaded {} DocStore entries from {}", entries.size(), storePath);
        } catch (Exception e) {
            log.warn("Failed to load DocStore from {}: {}", storePath, e.getMessage());
        }
    }

    private void flushToDisk() {
        try {
            Files.createDirectories(storePath.getParent());
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(storePath.toFile(), List.copyOf(entries.values()));
        } catch (Exception e) {
            log.error("Failed to flush DocStore to {}: {}", storePath, e.getMessage());
        }
    }
}
