package io.jaiclaw.examples.triage;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TicketStoreTool implements ToolCallback {

    private final ConcurrentHashMap<String, TriageRecord> store = new ConcurrentHashMap<>();

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "store_ticket",
                "Store a triaged support ticket with sentiment analysis and routing decision results",
                ToolCatalog.SECTION_CUSTOM,
                """
                {
                  "type": "object",
                  "properties": {
                    "message": { "type": "string", "description": "Original support message" },
                    "sentiment": { "type": "string", "description": "Detected sentiment (e.g., negative, positive, neutral)" },
                    "categories": { "type": "string", "description": "Comma-separated categories (e.g., technical, billing)" },
                    "priority": { "type": "string", "description": "Assigned priority (e.g., critical, high, medium, low)" },
                    "team": { "type": "string", "description": "Routed team (e.g., Tier-2 Engineering, Billing Support)" },
                    "decision": { "type": "string", "description": "Decision outcome (e.g., ESCALATE, ROUTE, AUTO_RESOLVE)" }
                  },
                  "required": ["message", "sentiment", "priority", "team", "decision"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        var record = new TriageRecord(
                ticketId,
                (String) parameters.get("message"),
                (String) parameters.get("sentiment"),
                (String) parameters.getOrDefault("categories", ""),
                (String) parameters.get("priority"),
                (String) parameters.get("team"),
                (String) parameters.get("decision"),
                Instant.now()
        );
        store.put(ticketId, record);

        return new ToolResult.Success(String.format(
                "{\"ticketId\":\"%s\",\"priority\":\"%s\",\"team\":\"%s\",\"decision\":\"%s\",\"status\":\"created\"}",
                ticketId, record.priority(), record.team(), record.decision()
        ));
    }

    public ConcurrentHashMap<String, TriageRecord> getStore() {
        return store;
    }
}
