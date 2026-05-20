package io.jaiclaw.examples.onboarding;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OnboardingStatusTool implements ToolCallback {

    private final SaveOnboardingTool saveOnboardingTool;

    public OnboardingStatusTool(SaveOnboardingTool saveOnboardingTool) {
        this.saveOnboardingTool = saveOnboardingTool;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "onboarding_status",
                "Look up an employee onboarding record by employee ID, name, or email",
                ToolCatalog.SECTION_CUSTOM,
                """
                {
                  "type": "object",
                  "properties": {
                    "query": { "type": "string", "description": "Employee ID, name, or email to search for" }
                  },
                  "required": ["query"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String query = ((String) parameters.get("query")).toLowerCase();
        var store = saveOnboardingTool.getStore();

        // Search by ID first
        if (store.containsKey(query.toUpperCase())) {
            return new ToolResult.Success(formatRecord(store.get(query.toUpperCase())));
        }

        // Search by name or email
        var match = store.values().stream()
                .filter(r -> r.name().toLowerCase().contains(query)
                        || r.email().toLowerCase().contains(query))
                .findFirst();

        return match
                .map(r -> new ToolResult.Success(formatRecord(r)))
                .orElse(new ToolResult.Success("{\"found\":false,\"message\":\"No onboarding record found for: " + query + "\"}"));
    }

    private String formatRecord(OnboardingRecord r) {
        return String.format(
                "{\"employeeId\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"age\":%d,\"completedAt\":\"%s\",\"warnings\":%s}",
                r.employeeId(), r.name(), r.email(), r.phone(), r.age(), r.completedAt(),
                r.warnings().isEmpty() ? "[]" : "[\"" + String.join("\",\"", r.warnings()) + "\"]"
        );
    }
}
