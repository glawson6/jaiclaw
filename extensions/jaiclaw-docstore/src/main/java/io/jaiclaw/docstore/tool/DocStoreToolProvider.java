package io.jaiclaw.docstore.tool;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.docstore.DocStoreService;
import io.jaiclaw.docstore.model.DocStoreEntry;
import io.jaiclaw.docstore.search.DocStoreSearchOptions;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides JaiClaw tools for interacting with the DocStore.
 * Register these via {@link io.jaiclaw.plugin.PluginApi#registerTool}.
 */
public class DocStoreToolProvider {

    private final DocStoreService service;

    public DocStoreToolProvider(DocStoreService service) {
        this.service = service;
    }

    public List<ToolCallback> tools() {
        return List.of(
                new SearchTool(),
                new ListTool(),
                new GetTool(),
                new TagTool(),
                new DescribeTool(),
                new AnalyzeTool(),
                new DeleteTool(),
                new AddUrlTool()
        );
    }

    class SearchTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("docstore_search",
                    "Search indexed documents, files, and URLs in the DocStore",
                    "docstore",
                    """
                    {"type":"object","properties":{
                      "query":{"type":"string","description":"Search query"},
                      "max_results":{"type":"integer","description":"Max results (default 10)"},
                      "tags":{"type":"string","description":"Comma-separated tag filter"},
                      "type":{"type":"string","description":"MIME type prefix filter (e.g. pdf, image)"}
                    },"required":["query"]}
                    """);
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String query = (String) parameters.get("query");
            int maxResults = parameters.containsKey("max_results")
                    ? ((Number) parameters.get("max_results")).intValue() : 10;
            Set<String> tags = parameters.containsKey("tags")
                    ? Set.of(((String) parameters.get("tags")).split(","))
                    : null;
            String type = (String) parameters.get("type");

            var options = new DocStoreSearchOptions(null, maxResults, tags, type, null, null);
            var results = service.search(query, options);

            if (results.isEmpty()) {
                return new ToolResult.Success("No results found for: " + query);
            }

            var sb = new StringBuilder("Search results for \"%s\":\n\n".formatted(query));
            for (int i = 0; i < results.size(); i++) {
                var r = results.get(i);
                sb.append("%d. %s (ID: %s)\n".formatted(i + 1, r.entry().displayName(), r.entry().shortId()));
                sb.append("   Score: %.2f | Tags: %s\n".formatted(r.score(), formatTags(r.entry().tags())));
                if (r.matchSnippet() != null && !r.matchSnippet().isEmpty()) {
                    sb.append("   %s\n".formatted(r.matchSnippet()));
                }
                sb.append("\n");
            }
            return new ToolResult.Success(sb.toString());
        }
    }

    class ListTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("docstore_list",
                    "List recent entries in the DocStore",
                    "docstore",
                    """
                    {"type":"object","properties":{
                      "limit":{"type":"integer","description":"Max entries (default 10)"},
                      "type":{"type":"string","description":"MIME type prefix filter"}
                    },"required":[]}
                    """);
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            int limit = parameters.containsKey("limit")
                    ? ((Number) parameters.get("limit")).intValue() : 10;
            String type = (String) parameters.get("type");

            List<DocStoreEntry> entries;
            if (type != null && !type.isBlank()) {
                String prefix = type.contains("/") ? type : type + "/";
                if ("pdf".equals(type)) prefix = "application/pdf";
                entries = service.listByType(prefix, null, limit);
            } else {
                entries = service.list(null, limit, 0);
            }

            if (entries.isEmpty()) {
                return new ToolResult.Success("DocStore is empty.");
            }

            var sb = new StringBuilder("DocStore entries (%d):\n\n".formatted(entries.size()));
            for (int i = 0; i < entries.size(); i++) {
                var e = entries.get(i);
                sb.append("%d. %s (ID: %s) — %s\n".formatted(
                        i + 1, e.displayName(), e.shortId(), formatTags(e.tags())));
                if (e.description() != null) sb.append("   %s\n".formatted(e.description()));
            }
            return new ToolResult.Success(sb.toString());
        }
    }

    class GetTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("docstore_get",
                    "Get detailed information about a DocStore entry by ID",
                    "docstore",
                    """
                    {"type":"object","properties":{
                      "id":{"type":"string","description":"Entry ID"}
                    },"required":["id"]}
                    """);
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String id = (String) parameters.get("id");
            var entry = service.get(id).orElse(null);
            if (entry == null) return new ToolResult.Error("Entry not found: " + id);

            var sb = new StringBuilder();
            sb.append("Entry: %s\n".formatted(entry.displayName()));
            sb.append("ID: %s\n".formatted(entry.id()));
            sb.append("Type: %s\n".formatted(entry.entryType()));
            if (entry.mimeType() != null) sb.append("MIME: %s\n".formatted(entry.mimeType()));
            if (entry.fileSize() > 0) sb.append("Size: %s\n".formatted(formatSize(entry.fileSize())));
            sb.append("Tags: %s\n".formatted(formatTags(entry.tags())));
            if (entry.description() != null) sb.append("Description: %s\n".formatted(entry.description()));
            if (entry.sourceUrl() != null) sb.append("URL: %s\n".formatted(entry.sourceUrl()));
            sb.append("Indexed: %s\n".formatted(entry.indexedAt()));
            if (entry.analysis() != null) {
                sb.append("\nAnalysis (%s):\n".formatted(entry.analysis().level()));
                sb.append("  Summary: %s\n".formatted(entry.analysis().summary()));
                if (!entry.analysis().topics().isEmpty())
                    sb.append("  Topics: %s\n".formatted(String.join(", ", entry.analysis().topics())));
                if (!entry.analysis().entities().isEmpty())
                    sb.append("  Entities: %s\n".formatted(String.join(", ", entry.analysis().entities())));
            }
            return new ToolResult.Success(sb.toString());
        }
    }

    class TagTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("docstore_tag",
                    "Add tags to a DocStore entry",
                    "docstore",
                    """
                    {"type":"object","properties":{
                      "id":{"type":"string","description":"Entry ID"},
                      "tags":{"type":"string","description":"Comma-separated tags to add"}
                    },"required":["id","tags"]}
                    """);
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String id = (String) parameters.get("id");
            Set<String> tags = Arrays.stream(((String) parameters.get("tags")).split("[,\\s]+"))
                    .map(t -> t.startsWith("#") ? t.substring(1) : t)
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            var updated = service.tag(id, tags);
            if (updated == null) return new ToolResult.Error("Entry not found: " + id);
            return new ToolResult.Success("Tagged %s: %s".formatted(id, formatTags(updated.tags())));
        }
    }

    class DescribeTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("docstore_describe",
                    "Set or update the description of a DocStore entry",
                    "docstore",
                    """
                    {"type":"object","properties":{
                      "id":{"type":"string","description":"Entry ID"},
                      "description":{"type":"string","description":"Description text"}
                    },"required":["id","description"]}
                    """);
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String id = (String) parameters.get("id");
            String description = (String) parameters.get("description");
            var updated = service.describe(id, description);
            if (updated == null) return new ToolResult.Error("Entry not found: " + id);
            return new ToolResult.Success("Description updated for %s.".formatted(id));
        }
    }

    class AnalyzeTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("docstore_analyze",
                    "Trigger document analysis on a DocStore entry",
                    "docstore",
                    """
                    {"type":"object","properties":{
                      "id":{"type":"string","description":"Entry ID to analyze"}
                    },"required":["id"]}
                    """);
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String id = (String) parameters.get("id");
            var entry = service.get(id).orElse(null);
            if (entry == null) return new ToolResult.Error("Entry not found: " + id);

            // Analysis requires content bytes — caller must provide them via channel-specific download
            return new ToolResult.Success(
                    "Analysis requested for %s. Use the channel-specific download to provide file content."
                            .formatted(entry.displayName()));
        }
    }

    class DeleteTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("docstore_delete",
                    "Remove an entry from the DocStore index",
                    "docstore",
                    """
                    {"type":"object","properties":{
                      "id":{"type":"string","description":"Entry ID to delete"}
                    },"required":["id"]}
                    """);
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String id = (String) parameters.get("id");
            var entry = service.get(id).orElse(null);
            if (entry == null) return new ToolResult.Error("Entry not found: " + id);
            service.delete(id);
            return new ToolResult.Success("Deleted: %s (%s)".formatted(entry.displayName(), id));
        }
    }

    class AddUrlTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("docstore_add_url",
                    "Index a URL in the DocStore",
                    "docstore",
                    """
                    {"type":"object","properties":{
                      "url":{"type":"string","description":"URL to index"},
                      "tags":{"type":"string","description":"Comma-separated tags"},
                      "description":{"type":"string","description":"Description of the URL"}
                    },"required":["url"]}
                    """);
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String url = (String) parameters.get("url");
            var entry = service.addUrl(url, null, null, "agent");

            if (parameters.containsKey("tags")) {
                Set<String> tags = Arrays.stream(((String) parameters.get("tags")).split("[,\\s]+"))
                        .map(t -> t.startsWith("#") ? t.substring(1) : t)
                        .filter(t -> !t.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                service.tag(entry.id(), tags);
            }
            if (parameters.containsKey("description")) {
                service.describe(entry.id(), (String) parameters.get("description"));
            }

            return new ToolResult.Success("Indexed URL: %s (ID: %s)".formatted(url, entry.shortId()));
        }
    }

    private static String formatTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) return "(none)";
        return tags.stream().map(t -> "#" + t).collect(Collectors.joining(" "));
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024.0));
    }
}
