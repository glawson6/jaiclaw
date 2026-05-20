package io.jaiclaw.examples.onboarding;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SaveOnboardingTool implements ToolCallback {

    private final ConcurrentHashMap<String, OnboardingRecord> store = new ConcurrentHashMap<>();

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "save_onboarding",
                "Save a validated employee onboarding record. Only call this after all fields pass validation.",
                ToolCatalog.SECTION_CUSTOM,
                """
                {
                  "type": "object",
                  "properties": {
                    "name": { "type": "string", "description": "Full name of the employee" },
                    "email": { "type": "string", "description": "Email address" },
                    "phone": { "type": "string", "description": "Phone number" },
                    "age": { "type": "integer", "description": "Age of the employee" },
                    "warnings": { "type": "array", "items": { "type": "string" }, "description": "Validation warnings (non-blocking)" }
                  },
                  "required": ["name", "email", "phone", "age"]
                }
                """
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String name = (String) parameters.get("name");
        String email = (String) parameters.get("email");
        String phone = (String) parameters.get("phone");
        int age = ((Number) parameters.get("age")).intValue();

        List<String> warnings = List.of();
        Object warningsObj = parameters.get("warnings");
        if (warningsObj instanceof List<?> list) {
            warnings = list.stream().map(Object::toString).toList();
        }

        String employeeId = "EMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var record = new OnboardingRecord(employeeId, name, email, phone, age, Instant.now(), warnings);
        store.put(employeeId, record);

        return new ToolResult.Success(String.format(
                "{\"employeeId\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"status\":\"completed\",\"warnings\":%d}",
                employeeId, name, email, warnings.size()
        ));
    }

    public ConcurrentHashMap<String, OnboardingRecord> getStore() {
        return store;
    }
}
