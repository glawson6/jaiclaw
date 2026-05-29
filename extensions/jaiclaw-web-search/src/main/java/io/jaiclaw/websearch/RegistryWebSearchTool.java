package io.jaiclaw.websearch;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry-backed web search tool that delegates to the active {@link WebSearchProvider}.
 * Uses tool name {@code web_search} to replace the built-in DuckDuckGo-only tool.
 */
public class RegistryWebSearchTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "The search query"
                },
                "maxResults": {
                  "type": "integer",
                  "description": "Maximum number of results to return (default 5)"
                }
              },
              "required": ["query"]
            }""";

    private final WebSearchRegistry registry;
    private final int defaultMaxResults;

    public RegistryWebSearchTool(WebSearchRegistry registry, int defaultMaxResults) {
        super(new ToolDefinition(
                "web_search",
                "Search the web for information using the configured search provider. Returns search results with titles, URLs, and snippets.",
                ToolCatalog.SECTION_WEB,
                INPUT_SCHEMA,
                Set.of(ToolProfile.CODING, ToolProfile.FULL)
        ));
        this.registry = registry;
        this.defaultMaxResults = defaultMaxResults;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String query = requireParam(parameters, "query");
        int maxResults = parameters.containsKey("maxResults")
                ? Integer.parseInt(parameters.get("maxResults").toString())
                : defaultMaxResults;

        WebSearchProvider provider = registry.activeProvider()
                .orElseThrow(() -> new IllegalStateException("No configured web search provider available"));

        List<WebSearchResult> results = provider.search(query, maxResults);

        if (results.isEmpty()) {
            return new ToolResult.Success("No results found for: " + query);
        }

        var sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            sb.append(i + 1).append(". ").append(r.title()).append('\n');
            sb.append("   URL: ").append(r.url()).append('\n');
            if (r.snippet() != null && !r.snippet().isEmpty()) {
                sb.append("   ").append(r.snippet()).append('\n');
            }
            sb.append('\n');
        }

        return new ToolResult.Success(sb.toString());
    }
}
