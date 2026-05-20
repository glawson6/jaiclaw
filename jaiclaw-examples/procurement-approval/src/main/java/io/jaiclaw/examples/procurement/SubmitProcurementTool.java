package io.jaiclaw.examples.procurement;

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
public class SubmitProcurementTool implements ToolCallback {

    private final ConcurrentHashMap<String, ProcurementRequest> store = new ConcurrentHashMap<>();

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "submit_procurement",
                "Submit a procurement request with all gathered details and rule-engine decisions",
                ToolCatalog.SECTION_CUSTOM,
                """
                {
                  "type": "object",
                  "properties": {
                    "requester": { "type": "string", "description": "Name of the person requesting" },
                    "description": { "type": "string", "description": "What is being procured" },
                    "amount": { "type": "number", "description": "Cost in USD" },
                    "vendor": { "type": "string", "description": "Vendor name" },
                    "vendorEmail": { "type": "string", "description": "Vendor contact email" },
                    "vendorPhone": { "type": "string", "description": "Vendor contact phone" },
                    "decision": { "type": "string", "description": "Rule engine decision: APPROVED, PENDING_APPROVAL, or ESCALATE" },
                    "priority": { "type": "string", "description": "Assigned priority: critical, high, medium, low" },
                    "assignedApprover": { "type": "string", "description": "Approver or team assigned" }
                  },
                  "required": ["requester", "description", "amount", "decision", "priority"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String requestId = "PRQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String decision = (String) parameters.get("decision");

        String status = switch (decision.toUpperCase()) {
            case "APPROVED" -> "approved";
            case "ESCALATE" -> "escalated";
            default -> "pending_approval";
        };

        var request = new ProcurementRequest(
                requestId,
                (String) parameters.get("requester"),
                (String) parameters.get("description"),
                ((Number) parameters.get("amount")).doubleValue(),
                (String) parameters.getOrDefault("vendor", ""),
                (String) parameters.getOrDefault("vendorEmail", ""),
                (String) parameters.getOrDefault("vendorPhone", ""),
                decision,
                (String) parameters.get("priority"),
                (String) parameters.getOrDefault("assignedApprover", ""),
                Instant.now(),
                status
        );
        store.put(requestId, request);

        return new ToolResult.Success(String.format(
                "{\"requestId\":\"%s\",\"amount\":%.2f,\"decision\":\"%s\",\"priority\":\"%s\",\"status\":\"%s\",\"assignedApprover\":\"%s\"}",
                requestId, request.amount(), decision, request.priority(), status, request.assignedApprover()
        ));
    }

    public ConcurrentHashMap<String, ProcurementRequest> getStore() {
        return store;
    }
}
