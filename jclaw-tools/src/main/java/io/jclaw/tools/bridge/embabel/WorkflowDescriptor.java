package io.jclaw.tools.bridge.embabel;

/**
 * Describes an available workflow/agent from the orchestration platform.
 *
 * @param name        workflow name
 * @param description human-readable description
 * @param inputSchema JSON schema for the expected input (nullable)
 */
public record WorkflowDescriptor(
        String name,
        String description,
        String inputSchema
) {
    public WorkflowDescriptor {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (description == null) description = "";
    }

    public static WorkflowDescriptor of(String name, String description) {
        return new WorkflowDescriptor(name, description, null);
    }
}
