package io.jaiclaw.wiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A wiki page with optional frontmatter, tags, and category.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WikiPage(
        String id,
        String title,
        String category,
        List<String> tags,
        String body,
        Map<String, String> frontmatter,
        Instant createdAt,
        Instant updatedAt,
        String tenantId
) {
    public WikiPage {
        if (tags == null) tags = List.of();
        if (frontmatter == null) frontmatter = Map.of();
    }

    public String toMarkdown() {
        var sb = new StringBuilder();
        if (!frontmatter.isEmpty()) {
            sb.append("---\n");
            frontmatter.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
            sb.append("---\n\n");
        }
        sb.append("# ").append(title).append("\n\n");
        if (category != null) sb.append("Category: ").append(category).append("\n");
        if (!tags.isEmpty()) sb.append("Tags: ").append(String.join(", ", tags)).append("\n");
        sb.append('\n');
        if (body != null) sb.append(body);
        return sb.toString();
    }

    public WikiPage withBody(String newBody) {
        return new WikiPage(id, title, category, tags, newBody, frontmatter,
                createdAt, Instant.now(), tenantId);
    }

    public WikiPage withTags(List<String> newTags) {
        return new WikiPage(id, title, category, newTags, body, frontmatter,
                createdAt, Instant.now(), tenantId);
    }
}
