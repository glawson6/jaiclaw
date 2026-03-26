package io.jaiclaw.examples.documentqa;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool for searching ingested documents.
 * In production, this would use VectorStoreSearchManager for semantic search.
 */
@Component
public class DocumentSearchTool implements ToolCallback {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "search_documents",
                "Search the knowledge base for relevant document passages",
                "document-qa",
                """
                {
                  "type": "object",
                  "properties": {
                    "query": { "type": "string", "description": "Search query" },
                    "maxResults": { "type": "integer", "description": "Max results to return", "default": 3 }
                  },
                  "required": ["query"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String query = (String) parameters.get("query");
        int maxResults = parameters.containsKey("maxResults")
                ? ((Number) parameters.get("maxResults")).intValue() : 3;

        // Simulated — in production, use VectorStoreSearchManager
        String results = String.format("""
                [
                  {"title":"Sample Document","passage":"This is a relevant passage matching '%s'...","score":0.92},
                  {"title":"FAQ Document","passage":"Related information about the query topic...","score":0.85}
                ]""", query);
        return new ToolResult.Success(results);
    }
}
