package io.jclaw.examples.meeting;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool that stores meeting summaries for later retrieval.
 * In production, this would integrate with Identity module for
 * cross-channel participant linking.
 */
@Component
public class SummaryTool implements ToolCallback {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "save_meeting_summary",
                "Save a meeting summary with action items",
                "meeting",
                """
                {
                  "type": "object",
                  "properties": {
                    "title": { "type": "string", "description": "Meeting title" },
                    "summary": { "type": "string", "description": "Meeting summary text" },
                    "action_items": {
                      "type": "array",
                      "items": { "type": "string" },
                      "description": "List of action items from the meeting"
                    },
                    "attendees": {
                      "type": "array",
                      "items": { "type": "string" },
                      "description": "List of meeting attendees"
                    }
                  },
                  "required": ["title", "summary"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String title = (String) parameters.get("title");
        String summary = (String) parameters.get("summary");
        // Simulated — in production, store in database and notify via Slack
        String result = String.format(
                "{\"status\":\"saved\",\"title\":\"%s\",\"summary_length\":%d}",
                title, summary.length()
        );
        return new ToolResult.Success(result);
    }
}
