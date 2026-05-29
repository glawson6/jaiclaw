package io.jaiclaw.wiki;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade for wiki operations.
 */
public class WikiService {

    private final WikiRepository repository;

    public WikiService(WikiRepository repository) {
        this.repository = repository;
    }

    public WikiPage createPage(String title, String category, List<String> tags,
                                String body, String tenantId) {
        var page = new WikiPage(
                UUID.randomUUID().toString(),
                title,
                category,
                tags,
                body,
                Map.of(),
                Instant.now(),
                Instant.now(),
                tenantId
        );
        repository.save(page);
        return page;
    }

    public Optional<WikiPage> getPage(String idOrTitle) {
        Optional<WikiPage> byId = repository.findById(idOrTitle);
        return byId.isPresent() ? byId : repository.findByTitle(idOrTitle);
    }

    public Optional<WikiPage> updateBody(String idOrTitle, String newBody) {
        return getPage(idOrTitle).map(page -> {
            WikiPage updated = page.withBody(newBody);
            repository.save(updated);
            return updated;
        });
    }

    public boolean deletePage(String id) {
        if (repository.findById(id).isPresent()) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }

    public List<WikiPage> listByCategory(String category) {
        return category != null ? repository.findByCategory(category) : repository.findAll();
    }

    public List<WikiPage> searchByTag(String tag) {
        return repository.findByTag(tag);
    }

    public String buildCorpus() {
        List<WikiPage> all = repository.findAll();
        if (all.isEmpty()) return "";
        var sb = new StringBuilder();
        for (WikiPage page : all) {
            sb.append(page.toMarkdown()).append("\n---\n\n");
        }
        return sb.toString();
    }

    public long count() {
        return repository.count();
    }
}
