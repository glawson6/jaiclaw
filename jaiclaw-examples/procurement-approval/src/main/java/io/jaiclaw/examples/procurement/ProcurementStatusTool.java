package io.jaiclaw.examples.procurement;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProcurementStatusTool implements ToolCallback {

    private final SubmitProcurementTool submitProcurementTool;

    public ProcurementStatusTool(SubmitProcurementTool submitProcurementTool) {
        this.submitProcurementTool = submitProcurementTool;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "procurement_status",
                "Check the status of a procurement request by request ID",
                ToolCatalog.SECTION_CUSTOM,
                """
                {
                  "type": "object",
                  "properties": {
                    "requestId": { "type": "string", "description": "The procurement request ID (e.g., PRQ-ABC12345)" }
                  },
                  "required": ["requestId"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String requestId = ((String) parameters.get("requestId")).toUpperCase();
        var store = submitProcurementTool.getStore();
        var request = store.get(requestId);

        if (request == null) {
            return new ToolResult.Success("{\"found\":false,\"message\":\"No procurement request found with ID: " + requestId + "\"}");
        }

        return new ToolResult.Success(String.format(
                "{\"requestId\":\"%s\",\"requester\":\"%s\",\"description\":\"%s\",\"amount\":%.2f,\"vendor\":\"%s\",\"decision\":\"%s\",\"priority\":\"%s\",\"status\":\"%s\",\"submittedAt\":\"%s\"}",
                request.requestId(), request.requester(), request.description(), request.amount(),
                request.vendor(), request.decision(), request.priority(), request.status(), request.submittedAt()
        ));
    }
}
