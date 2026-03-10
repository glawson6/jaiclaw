package io.jclaw.core.tool;

import java.util.Set;

/**
 * Metadata describing a tool that can be provided to an LLM.
 * The {@code inputSchema} is a JSON Schema string describing the tool's parameters,
 * used by Spring AI to generate function-call payloads for the model.
 */
public record ToolDefinition(
        String name,
        String description,
        String section,
        String inputSchema,
        Set<ToolProfile> profiles
) {
    private static final String EMPTY_SCHEMA = """
            {"type":"object","properties":{},"required":[]}""";

    public ToolDefinition(String name, String description, String section, String inputSchema) {
        this(name, description, section, inputSchema, Set.of(ToolProfile.FULL));
    }

    public ToolDefinition(String name, String description, String section) {
        this(name, description, section, EMPTY_SCHEMA, Set.of(ToolProfile.FULL));
    }

    /**
     * Returns true if this tool should be available when the agent runs with the given profile.
     * A FULL profile grants access to all tools regardless of their tagged profiles.
     */
    public boolean isAvailableIn(ToolProfile profile) {
        return profile == ToolProfile.FULL || profiles.contains(profile);
    }
}
