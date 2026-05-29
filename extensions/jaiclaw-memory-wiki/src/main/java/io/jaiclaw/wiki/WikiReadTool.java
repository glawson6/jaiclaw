package io.jaiclaw.wiki;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Set;

public class WikiReadTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "id": { "type": "string", "description": "Page ID or title" }
              },
              "required": ["id"]
            }""";

    private final WikiService service;

    public WikiReadTool(WikiService service) {
        super(new ToolDefinition("wiki_read", "Read a wiki page by ID or title",
                ToolCatalog.SECTION_MEMORY, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.service = service;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String id = requireParam(parameters, "id");
        return service.getPage(id)
                .map(page -> (ToolResult) new ToolResult.Success(page.toMarkdown()))
                .orElse(new ToolResult.Error("Page not found: " + id));
    }
}
