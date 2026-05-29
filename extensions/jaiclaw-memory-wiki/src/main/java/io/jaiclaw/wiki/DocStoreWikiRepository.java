package io.jaiclaw.wiki;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.docstore.model.DocStoreEntry;
import io.jaiclaw.docstore.repository.DocStoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * WikiRepository implementation backed by DocStore.
 * Maps WikiPage fields to DocStoreEntry fields and delegates all persistence
 * to the underlying DocStoreRepository.
 */
public class DocStoreWikiRepository implements WikiRepository {

    private static final Logger log = LoggerFactory.getLogger(DocStoreWikiRepository.class);
    private static final String SCOPE_WIKI = "wiki";
    private static final String MIME_TYPE = "text/markdown";

    private final DocStoreRepository docStore;
    private final ObjectMapper mapper;

    public DocStoreWikiRepository(DocStoreRepository docStore) {
        this.docStore = docStore;
        this.mapper = new ObjectMapper();
    }

    @Override
    public void save(WikiPage page) {
        docStore.save(toEntry(page));
    }

    @Override
    public Optional<WikiPage> findById(String id) {
        return docStore.findById(id).map(this::toPage);
    }

    @Override
    public Optional<WikiPage> findByTitle(String title) {
        return allWikiEntries().stream()
                .filter(e -> title.equalsIgnoreCase(e.filename()))
                .findFirst()
                .map(this::toPage);
    }

    @Override
    public List<WikiPage> findByCategory(String category) {
        return allWikiEntries().stream()
                .filter(e -> category.equalsIgnoreCase(e.category()))
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .map(this::toPage)
                .toList();
    }

    @Override
    public List<WikiPage> findByTag(String tag) {
        return docStore.findByTags(Set.of(tag.toLowerCase()), SCOPE_WIKI).stream()
                .filter(e -> MIME_TYPE.equals(e.mimeType()))
                .map(this::toPage)
                .toList();
    }

    @Override
    public List<WikiPage> findAll() {
        return allWikiEntries().stream()
                .sorted(Comparator.comparing(DocStoreEntry::indexedAt).reversed())
                .map(this::toPage)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        docStore.deleteById(id);
    }

    @Override
    public long count() {
        return docStore.count(SCOPE_WIKI);
    }

    private List<DocStoreEntry> allWikiEntries() {
        return docStore.findRecent(SCOPE_WIKI, Integer.MAX_VALUE).stream()
                .filter(e -> MIME_TYPE.equals(e.mimeType()))
                .toList();
    }

    DocStoreEntry toEntry(WikiPage page) {
        String frontmatterJson = null;
        if (page.frontmatter() != null && !page.frontmatter().isEmpty()) {
            try {
                frontmatterJson = mapper.writeValueAsString(page.frontmatter());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize frontmatter for page {}: {}", page.id(), e.getMessage());
            }
        }

        Set<String> tags = page.tags() != null
                ? page.tags().stream().map(String::toLowerCase).collect(Collectors.toSet())
                : Set.of();

        return DocStoreEntry.builder()
                .id(page.id())
                .entryType(DocStoreEntry.EntryType.FILE)
                .filename(page.title())
                .mimeType(MIME_TYPE)
                .fileSize(page.body() != null ? page.body().length() : 0)
                .channelId(SCOPE_WIKI)
                .channelFileRef(page.updatedAt() != null ? page.updatedAt().toString() : null)
                .channelMessageRef(frontmatterJson)
                .userId(SCOPE_WIKI)
                .chatId(SCOPE_WIKI)
                .indexedAt(page.createdAt())
                .tags(tags)
                .description(page.body())
                .category(page.category())
                .tenantId(page.tenantId())
                .build();
    }

    WikiPage toPage(DocStoreEntry entry) {
        Map<String, String> frontmatter = Map.of();
        if (entry.channelMessageRef() != null && !entry.channelMessageRef().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> parsed = mapper.readValue(entry.channelMessageRef(), Map.class);
                frontmatter = parsed != null ? parsed : Map.of();
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize frontmatter for entry {}: {}", entry.id(), e.getMessage());
            }
        }

        Instant updatedAt = entry.indexedAt();
        if (entry.channelFileRef() != null && !entry.channelFileRef().isEmpty()) {
            try {
                updatedAt = Instant.parse(entry.channelFileRef());
            } catch (Exception e) {
                log.warn("Failed to parse updatedAt for entry {}: {}", entry.id(), e.getMessage());
            }
        }

        List<String> tags = entry.tags() != null ? List.copyOf(entry.tags()) : List.of();

        return new WikiPage(
                entry.id(),
                entry.filename(),
                entry.category(),
                tags,
                entry.description(),
                frontmatter,
                entry.indexedAt(),
                updatedAt,
                entry.tenantId()
        );
    }
}
