package io.jaiclaw.core.tool;

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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private String description;
        private String section;
        private String inputSchema;
        private Set<ToolProfile> profiles;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder section(String section) { this.section = section; return this; }
        public Builder inputSchema(String inputSchema) { this.inputSchema = inputSchema; return this; }
        public Builder profiles(Set<ToolProfile> profiles) { this.profiles = profiles; return this; }

        public ToolDefinition build() {
            return new ToolDefinition(name, description, section, inputSchema, profiles);
        }
    }
}
