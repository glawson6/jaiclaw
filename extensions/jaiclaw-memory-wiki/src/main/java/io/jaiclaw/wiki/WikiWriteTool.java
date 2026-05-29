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

public class WikiWriteTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "title": { "type": "string", "description": "Page title" },
                "body": { "type": "string", "description": "Page body content (Markdown)" },
                "category": { "type": "string", "description": "Page category (optional)" },
                "tags": { "type": "string", "description": "Comma-separated tags (optional)" }
              },
              "required": ["title", "body"]
            }""";

    private final WikiService service;

    public WikiWriteTool(WikiService service) {
        super(new ToolDefinition("wiki_write", "Create or update a wiki page",
                ToolCatalog.SECTION_MEMORY, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.service = service;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String title = requireParam(parameters, "title");
        String body = requireParam(parameters, "body");
        String category = optionalParam(parameters, "category", null);
        String tagsStr = optionalParam(parameters, "tags", "");
        List<String> tags = tagsStr != null && !tagsStr.isEmpty()
                ? List.of(tagsStr.split(",\\s*"))
                : List.of();

        // Try to update existing page first
        var existing = service.getPage(title);
        if (existing.isPresent()) {
            service.updateBody(title, body);
            return new ToolResult.Success("Updated wiki page: " + title);
        }

        service.createPage(title, category, tags, body, null);
        return new ToolResult.Success("Created wiki page: " + title);
    }
}
