package io.jaiclaw.examples.procurement;

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
public class ProcurementListTool implements ToolCallback {

    private final SubmitProcurementTool submitProcurementTool;

    public ProcurementListTool(SubmitProcurementTool submitProcurementTool) {
        this.submitProcurementTool = submitProcurementTool;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "procurement_list",
                "List recent procurement requests, optionally filtered by status or priority",
                ToolCatalog.SECTION_CUSTOM,
                """
                {
                  "type": "object",
                  "properties": {
                    "status": { "type": "string", "description": "Filter by status: approved, pending_approval, escalated" },
                    "priority": { "type": "string", "description": "Filter by priority: critical, high, medium, low" },
                    "limit": { "type": "integer", "description": "Maximum number of results (default: 10)" }
                  }
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String statusFilter = (String) parameters.get("status");
        String priorityFilter = (String) parameters.get("priority");
        int limit = parameters.containsKey("limit") ? ((Number) parameters.get("limit")).intValue() : 10;

        var store = submitProcurementTool.getStore();
        var results = store.values().stream()
                .filter(r -> statusFilter == null || r.status().equalsIgnoreCase(statusFilter))
                .filter(r -> priorityFilter == null || r.priority().equalsIgnoreCase(priorityFilter))
                .sorted(Comparator.comparing(ProcurementRequest::submittedAt).reversed())
                .limit(limit)
                .map(r -> String.format(
                        "{\"requestId\":\"%s\",\"description\":\"%s\",\"amount\":%.2f,\"decision\":\"%s\",\"status\":\"%s\"}",
                        r.requestId(), r.description(), r.amount(), r.decision(), r.status()))
                .collect(Collectors.joining(","));

        return new ToolResult.Success("{\"total\":" + store.size() + ",\"requests\":[" + results + "]}");
    }
}
