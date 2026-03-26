package io.jaiclaw.examples.briefing;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Simulated news tool. In production, replace with a real news API call
 * (e.g., NewsAPI, RSS feed parser).
 */
@Component
public class NewsTool implements ToolCallback {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "get_news",
                "Get top news headlines for a given topic",
                "briefing",
                """
                {
                  "type": "object",
                  "properties": {
                    "topic": { "type": "string", "description": "News topic (e.g., technology, business)" },
                    "count": { "type": "integer", "description": "Number of headlines to return", "default": 5 }
                  },
                  "required": ["topic"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String topic = (String) parameters.get("topic");
        int count = parameters.containsKey("count") ? ((Number) parameters.get("count")).intValue() : 5;
        // Simulated response — replace with real news API
        StringBuilder news = new StringBuilder("[");
        for (int i = 1; i <= count; i++) {
            if (i > 1) news.append(",");
            news.append(String.format(
                    "{\"title\":\"Sample %s headline #%d\",\"source\":\"TechNews\",\"url\":\"https://example.com/%d\"}",
                    topic, i, i
            ));
        }
        news.append("]");
        return new ToolResult.Success(news.toString());
    }
}
