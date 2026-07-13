package io.jaiclaw.docs;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * MCP tool provider that exposes a {@code search_docs} tool for full-text search
 * across JaiClaw documentation.
 */
public class DocsMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(DocsMcpToolProvider.class);
    private static final String SERVER_NAME = "docs";
    private static final String SEARCH_TOOL = "search_docs";

    private static final String SEARCH_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "Search query — keywords to find in documentation"
                },
                "maxResults": {
                  "type": "integer",
                  "description": "Maximum number of results to return (default: 5)",
                  "default": 5
                }
              },
              "required": ["query"]
            }
            """;

    private final DocsRepository repository;
    private final ObjectMapper objectMapper;

    public DocsMcpToolProvider(DocsRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getServerName() {
        return SERVER_NAME;
    }

    @Override
    public String getServerDescription() {
        return "Search JaiClaw documentation";
    }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(new McpToolDefinition(
                SEARCH_TOOL,
                "Search JaiClaw documentation by keyword. Returns matching document URIs, names, snippets, and relevance scores.",
                SEARCH_SCHEMA
        ));
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        if (!SEARCH_TOOL.equals(toolName)) {
            return McpToolResult.error("Unknown tool: " + toolName);
        }

        Object queryObj = args.get("query");
        if (queryObj == null || queryObj.toString().isBlank()) {
            return McpToolResult.error("Missing required parameter: query");
        }

        String query = queryObj.toString();
        int maxResults = 5;
        Object maxObj = args.get("maxResults");
        if (maxObj instanceof Number n) {
            maxResults = n.intValue();
        }

        List<DocsSearchResult> results = repository.search(query, maxResults);

        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                    Map.of("query", query, "resultCount", results.size(), "results", results));
            return McpToolResult.success(json);
        } catch (Exception e) {
            log.error("Failed to serialize search results", e);
            return McpToolResult.error("Failed to serialize results: " + e.getMessage());
        }
    }
}
