package io.jclaw.examples.helpdesk;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Tool for creating support tickets when the FAQ doesn't have an answer.
 */
@Component
public class TicketTool implements ToolCallback {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "create_ticket",
                "Create a support ticket for issues that need human attention",
                "helpdesk",
                """
                {
                  "type": "object",
                  "properties": {
                    "subject": { "type": "string", "description": "Ticket subject" },
                    "description": { "type": "string", "description": "Detailed description of the issue" },
                    "priority": { "type": "string", "description": "Priority: low, medium, high", "default": "medium" }
                  },
                  "required": ["subject", "description"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String subject = (String) parameters.get("subject");
        String priority = (String) parameters.getOrDefault("priority", "medium");
        String ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String result = String.format(
                "{\"ticket_id\":\"%s\",\"subject\":\"%s\",\"priority\":\"%s\",\"status\":\"open\"}",
                ticketId, subject, priority
        );
        return new ToolResult.Success(result);
    }
}
