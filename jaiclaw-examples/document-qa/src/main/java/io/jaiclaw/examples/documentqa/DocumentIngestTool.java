package io.jaiclaw.examples.documentqa;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool for ingesting documents into the knowledge base.
 * In production, this would use DocumentParser to parse PDFs/HTML
 * and VectorStore for semantic indexing.
 */
@Component
public class DocumentIngestTool implements ToolCallback {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "ingest_document",
                "Ingest a document into the knowledge base for Q&A",
                "document-qa",
                """
                {
                  "type": "object",
                  "properties": {
                    "title": { "type": "string", "description": "Document title" },
                    "content": { "type": "string", "description": "Document text content" },
                    "source": { "type": "string", "description": "Source file path or URL" }
                  },
                  "required": ["title", "content"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String title = (String) parameters.get("title");
        String content = (String) parameters.get("content");
        String source = (String) parameters.getOrDefault("source", "manual");

        // Simulated — in production, parse with DocumentParser, chunk, and store in VectorStore
        int chunks = (content.length() / 500) + 1;
        String result = String.format(
                "{\"status\":\"ingested\",\"title\":\"%s\",\"source\":\"%s\",\"chunks\":%d,\"characters\":%d}",
                title, source, chunks, content.length()
        );
        return new ToolResult.Success(result);
    }
}
