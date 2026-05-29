package io.jaiclaw.wiki;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Set;

public class WikiDeleteTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "id": { "type": "string", "description": "Page ID to delete" }
              },
              "required": ["id"]
            }""";

    private final WikiService service;

    public WikiDeleteTool(WikiService service) {
        super(new ToolDefinition("wiki_delete", "Delete a wiki page by ID",
                ToolCatalog.SECTION_MEMORY, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.service = service;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String id = requireParam(parameters, "id");
        if (service.deletePage(id)) {
            return new ToolResult.Success("Deleted wiki page: " + id);
        }
        return new ToolResult.Error("Page not found: " + id);
    }
}
