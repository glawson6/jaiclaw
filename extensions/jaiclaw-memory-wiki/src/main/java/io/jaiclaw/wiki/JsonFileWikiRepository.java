package io.jaiclaw.wiki;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * JSON file-backed wiki repository following the DocStore persistence pattern.
 */
public class JsonFileWikiRepository implements WikiRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonFileWikiRepository.class);

    private final Path storePath;
    private final ObjectMapper mapper;
    private final Map<String, WikiPage> pages = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;

    public JsonFileWikiRepository(Path storagePath) {
        this(storagePath, null);
    }

    public JsonFileWikiRepository(Path storagePath, TenantGuard tenantGuard) {
        this.storePath = storagePath.resolve("wiki.json");
        this.tenantGuard = tenantGuard;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        loadFromDisk();
    }

    private Stream<WikiPage> tenantFiltered() {
        Stream<WikiPage> stream = pages.values().stream();
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            stream = stream.filter(p -> tenantId.equals(p.tenantId()));
        }
        return stream;
    }

    @Override
    public void save(WikiPage page) {
        pages.put(page.id(), page);
        flushToDisk();
    }

    @Override
    public Optional<WikiPage> findById(String id) {
        WikiPage page = pages.get(id);
        if (page != null && tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (!tenantId.equals(page.tenantId())) return Optional.empty();
        }
        return Optional.ofNullable(page);
    }

    @Override
    public Optional<WikiPage> findByTitle(String title) {
        return tenantFiltered()
                .filter(p -> title.equalsIgnoreCase(p.title()))
                .findFirst();
    }

    @Override
    public List<WikiPage> findByCategory(String category) {
        return tenantFiltered()
                .filter(p -> category.equalsIgnoreCase(p.category()))
                .sorted(Comparator.comparing(WikiPage::updatedAt).reversed())
                .toList();
    }

    @Override
    public List<WikiPage> findByTag(String tag) {
        return tenantFiltered()
                .filter(p -> p.tags() != null && p.tags().stream()
                        .anyMatch(t -> t.equalsIgnoreCase(tag)))
                .sorted(Comparator.comparing(WikiPage::updatedAt).reversed())
                .toList();
    }

    @Override
    public List<WikiPage> findAll() {
        return tenantFiltered()
                .sorted(Comparator.comparing(WikiPage::updatedAt).reversed())
                .toList();
    }

    @Override
    public void deleteById(String id) {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            WikiPage page = pages.get(id);
            if (page == null) return;
            String tenantId = tenantGuard.requireTenantIfMulti();
            if (!tenantId.equals(page.tenantId())) return;
        }
        pages.remove(id);
        flushToDisk();
    }

    @Override
    public long count() {
        return tenantFiltered().count();
    }

    private void loadFromDisk() {
        if (!Files.exists(storePath)) return;
        try {
            List<WikiPage> loaded = mapper.readValue(storePath.toFile(),
                    new TypeReference<List<WikiPage>>() {});
            loaded.forEach(p -> pages.put(p.id(), p));
            log.info("Loaded {} wiki pages from {}", pages.size(), storePath);
        } catch (IOException e) {
            log.warn("Failed to load wiki from {}: {}", storePath, e.getMessage());
        }
    }

    private void flushToDisk() {
        try {
            Files.createDirectories(storePath.getParent());
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(storePath.toFile(), List.copyOf(pages.values()));
        } catch (IOException e) {
            log.error("Failed to flush wiki to {}: {}", storePath, e.getMessage());
        }
    }
}
