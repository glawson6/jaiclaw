package io.jaiclaw.examples.helpdesk;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool for searching FAQ entries. Demonstrates per-tenant tool behavior —
 * the ToolContext carries the tenant ID from the security context.
 */
@Component
public class FaqTool implements ToolCallback {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "search_faq",
                "Search the FAQ knowledge base for answers to common questions",
                "helpdesk",
                """
                {
                  "type": "object",
                  "properties": {
                    "question": { "type": "string", "description": "The user's question" },
                    "category": { "type": "string", "description": "FAQ category (billing, technical, account)" }
                  },
                  "required": ["question"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String question = (String) parameters.get("question");
        String category = (String) parameters.getOrDefault("category", "general");

        // Simulated FAQ search — in production, this would query a tenant-specific KB
        String result = String.format("""
                {
                  "question": "%s",
                  "category": "%s",
                  "matches": [
                    {
                      "question": "How do I reset my password?",
                      "answer": "Go to Settings > Security > Reset Password. You'll receive an email with a reset link.",
                      "relevance": 0.95
                    },
                    {
                      "question": "What are the supported payment methods?",
                      "answer": "We accept Visa, Mastercard, PayPal, and bank transfers.",
                      "relevance": 0.72
                    }
                  ]
                }""", question, category);
        return new ToolResult.Success(result);
    }
}
