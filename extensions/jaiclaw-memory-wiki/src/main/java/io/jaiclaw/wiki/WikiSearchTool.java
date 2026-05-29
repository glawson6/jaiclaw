package io.jaiclaw.wiki;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class WikiSearchTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "tag": { "type": "string", "description": "Tag to search for" },
                "keyword": { "type": "string", "description": "Keyword to search in page titles and body" }
              }
            }""";

    private final WikiService service;

    public WikiSearchTool(WikiService service) {
        super(new ToolDefinition("wiki_search", "Search wiki pages by tag or keyword",
                ToolCatalog.SECTION_MEMORY, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.service = service;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String tag = optionalParam(parameters, "tag", null);
        String keyword = optionalParam(parameters, "keyword", null);

        List<WikiPage> results;
        if (tag != null && !tag.isEmpty()) {
            results = service.searchByTag(tag);
        } else if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword.toLowerCase();
            results = service.listByCategory(null).stream()
                    .filter(p -> (p.title() != null && p.title().toLowerCase().contains(kw))
                            || (p.body() != null && p.body().toLowerCase().contains(kw)))
                    .toList();
        } else {
            return new ToolResult.Error("Provide either 'tag' or 'keyword' parameter");
        }

        if (results.isEmpty()) return new ToolResult.Success("No pages found");

        var sb = new StringBuilder();
        for (WikiPage p : results) {
            sb.append("- **").append(p.title()).append("** (id: ").append(p.id()).append(")");
            if (p.category() != null) sb.append(" [").append(p.category()).append("]");
            sb.append('\n');
        }
        return new ToolResult.Success(sb.toString());
    }
}
