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

public class WikiListTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "category": { "type": "string", "description": "Filter by category (optional)" }
              }
            }""";

    private final WikiService service;

    public WikiListTool(WikiService service) {
        super(new ToolDefinition("wiki_list", "List wiki pages, optionally by category",
                ToolCatalog.SECTION_MEMORY, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.service = service;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String category = optionalParam(parameters, "category", null);
        List<WikiPage> pages = service.listByCategory(category);

        if (pages.isEmpty()) {
            return new ToolResult.Success("No wiki pages found" +
                    (category != null ? " in category: " + category : ""));
        }

        var sb = new StringBuilder();
        sb.append("Wiki pages (").append(pages.size()).append("):\n");
        for (WikiPage p : pages) {
            sb.append("- **").append(p.title()).append("** (id: ").append(p.id()).append(")");
            if (p.category() != null) sb.append(" [").append(p.category()).append("]");
            if (!p.tags().isEmpty()) sb.append(" tags: ").append(String.join(", ", p.tags()));
            sb.append('\n');
        }
        return new ToolResult.Success(sb.toString());
    }
}
