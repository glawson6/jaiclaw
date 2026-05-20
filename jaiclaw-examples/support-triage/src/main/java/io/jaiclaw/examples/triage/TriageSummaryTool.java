package io.jaiclaw.examples.triage;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TriageSummaryTool implements ToolCallback {

    private final TicketStoreTool ticketStoreTool;

    public TriageSummaryTool(TicketStoreTool ticketStoreTool) {
        this.ticketStoreTool = ticketStoreTool;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "triage_summary",
                "List recent triaged tickets, optionally filtered by priority or team",
                ToolCatalog.SECTION_CUSTOM,
                """
                {
                  "type": "object",
                  "properties": {
                    "priority": { "type": "string", "description": "Filter by priority (e.g., critical, high)" },
                    "team": { "type": "string", "description": "Filter by assigned team" },
                    "limit": { "type": "integer", "description": "Maximum number of results (default: 10)" }
                  }
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String priorityFilter = (String) parameters.get("priority");
        String teamFilter = (String) parameters.get("team");
        int limit = parameters.containsKey("limit") ? ((Number) parameters.get("limit")).intValue() : 10;

        var store = ticketStoreTool.getStore();
        var results = store.values().stream()
                .filter(r -> priorityFilter == null || r.priority().equalsIgnoreCase(priorityFilter))
                .filter(r -> teamFilter == null || r.team().equalsIgnoreCase(teamFilter))
                .sorted(Comparator.comparing(TriageRecord::timestamp).reversed())
                .limit(limit)
                .map(r -> String.format(
                        "{\"ticketId\":\"%s\",\"sentiment\":\"%s\",\"priority\":\"%s\",\"team\":\"%s\",\"decision\":\"%s\"}",
                        r.ticketId(), r.sentiment(), r.priority(), r.team(), r.decision()))
                .collect(Collectors.joining(","));

        return new ToolResult.Success("{\"total\":" + store.size() + ",\"tickets\":[" + results + "]}");
    }
}
