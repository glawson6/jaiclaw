package io.jaiclaw.core.tool;

import io.jaiclaw.core.api.Stable;

import java.util.Set;

/**
 * Metadata describing a tool that can be provided to an LLM.
 * The {@code inputSchema} is a JSON Schema string describing the tool's parameters,
 * used by Spring AI to generate function-call payloads for the model.
 *
 * <p>0.8.0 P3.5: {@link Stable}.
 *
 * <p>1.2.0 adds {@code deferred}, {@code keywords} and {@code source} for Tool
 * Search. They are appended <em>after</em> the original five components and every
 * pre-1.2.0 constructor is retained as a delegating overload, so all ~160 existing
 * call sites compile and behave exactly as before. A tool is only deferred if
 * something explicitly marks it so.
 *
 * @param name        tool name as the model sees it
 * @param description one-line description sent to the model
 * @param section     grouping used by tool policies (e.g. {@code files}, {@code exec})
 * @param inputSchema JSON Schema for the tool's parameters
 * @param profiles    profiles this tool is available in
 * @param deferred    when true the schema is withheld from the model until
 *                    {@code tool_search} surfaces it. Never set implicitly.
 * @param keywords    extra search terms for {@code tool_search}; the index also
 *                    covers name, description and section
 * @param source      where the tool came from — {@code builtin}, {@code mcp},
 *                    {@code camel}, … Used to defer whole classes of tools at once.
 */
@Stable
public record ToolDefinition(
        String name,
        String description,
        String section,
        String inputSchema,
        Set<ToolProfile> profiles,
        boolean deferred,
        Set<String> keywords,
        String source
) {
    private static final String EMPTY_SCHEMA = """
            {"type":"object","properties":{},"required":[]}""";

    /** Default source for tools registered directly, with no provider bridge. */
    public static final String SOURCE_BUILTIN = "builtin";

    /** Source tag applied to tools bridged in from an MCP server. */
    public static final String SOURCE_MCP = "mcp";

    /** Source tag applied to tools bridged in from Apache Camel endpoints. */
    public static final String SOURCE_CAMEL = "camel";

    public ToolDefinition {
        profiles = profiles == null ? Set.of(ToolProfile.FULL) : Set.copyOf(profiles);
        keywords = keywords == null ? Set.of() : Set.copyOf(keywords);
        if (source == null || source.isBlank()) source = SOURCE_BUILTIN;
    }

    /**
     * Pre-1.2.0 canonical constructor. Retained so the ~86 five-argument call
     * sites keep compiling; the tool is not deferred and has no extra keywords.
     */
    public ToolDefinition(String name, String description, String section,
                          String inputSchema, Set<ToolProfile> profiles) {
        this(name, description, section, inputSchema, profiles, false, Set.of(), SOURCE_BUILTIN);
    }

    public ToolDefinition(String name, String description, String section, String inputSchema) {
        this(name, description, section, inputSchema, Set.of(ToolProfile.FULL),
                false, Set.of(), SOURCE_BUILTIN);
    }

    public ToolDefinition(String name, String description, String section) {
        this(name, description, section, EMPTY_SCHEMA, Set.of(ToolProfile.FULL),
                false, Set.of(), SOURCE_BUILTIN);
    }

    /**
     * Returns true if this tool should be available when the agent runs with the given profile.
     * A FULL profile grants access to all tools regardless of their tagged profiles.
     *
     * <p>Deliberately unaffected by {@link #deferred()}: deferral controls whether the
     * model is <em>shown</em> the schema up front, not whether the tool is permitted.
     * Profile filtering remains the authorization boundary.
     */
    public boolean isAvailableIn(ToolProfile profile) {
        return profile == ToolProfile.FULL || profiles.contains(profile);
    }

    /** A copy of this definition marked deferred (or not). */
    public ToolDefinition withDeferred(boolean value) {
        return value == deferred ? this
                : new ToolDefinition(name, description, section, inputSchema, profiles,
                value, keywords, source);
    }

    /** A copy of this definition tagged with the given source. */
    public ToolDefinition withSource(String newSource) {
        return new ToolDefinition(name, description, section, inputSchema, profiles,
                deferred, keywords, newSource);
    }

    /** A copy of this definition with additional search keywords. */
    public ToolDefinition withKeywords(Set<String> newKeywords) {
        return new ToolDefinition(name, description, section, inputSchema, profiles,
                deferred, newKeywords, source);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private String description;
        private String section;
        private String inputSchema;
        private Set<ToolProfile> profiles;
        private boolean deferred;
        private Set<String> keywords;
        private String source;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder section(String section) { this.section = section; return this; }
        public Builder inputSchema(String inputSchema) { this.inputSchema = inputSchema; return this; }
        public Builder profiles(Set<ToolProfile> profiles) { this.profiles = profiles; return this; }
        public Builder deferred(boolean deferred) { this.deferred = deferred; return this; }
        public Builder keywords(Set<String> keywords) { this.keywords = keywords; return this; }
        public Builder source(String source) { this.source = source; return this; }

        public ToolDefinition build() {
            return new ToolDefinition(name, description, section, inputSchema, profiles,
                    deferred, keywords, source);
        }
    }
}
