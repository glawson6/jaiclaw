package io.jaiclaw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Tool Search / deferred-schema policy, bound from {@code jaiclaw.tools.search}.
 *
 * <p><strong>Off by default.</strong> With {@code enabled=false} no tool is
 * deferred and {@code tool_search} is not registered, so the model receives
 * exactly the tool list it received before 1.2.0.
 *
 * <p>Deferral is a context-economy control, not a security control: a deferred
 * tool is still fully permitted, it is simply not advertised until searched for.
 * Use tool profiles and allow/deny policy to actually restrict access.
 *
 * <p>A top-level record rather than a field on {@link ToolsProperties}, for the
 * same reason as {@link DelegationProperties}: that record is bound as a nested
 * type and this codebase has documented Boot-4 nested-record binding fragility.
 *
 * @param enabled  master switch; false means nothing is deferred and no tool_search
 * @param names    exact tool names to defer
 * @param globs    glob patterns over tool names, e.g. {@code kubectl_*}
 * @param sections tool sections to defer wholesale, e.g. {@code k8s}
 * @param sources  tool sources to defer wholesale — {@code mcp}, {@code camel}, {@code builtin}
 * @param limit    default number of results a single {@code tool_search} returns
 */
@ConfigurationProperties(prefix = "jaiclaw.tools.search")
public record ToolSearchProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue List<String> names,
        @DefaultValue List<String> globs,
        @DefaultValue List<String> sections,
        @DefaultValue List<String> sources,
        @DefaultValue("5") int limit
) {

    public ToolSearchProperties {
        names = names == null ? List.of() : List.copyOf(names);
        globs = globs == null ? List.of() : List.copyOf(globs);
        sections = sections == null ? List.of() : List.copyOf(sections);
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (limit <= 0) limit = 5;
    }

    /** Programmatic defaults for tests and builders; never seen by the binder. */
    public static ToolSearchProperties defaults() {
        return new ToolSearchProperties(false, List.of(), List.of(), List.of(), List.of(), 5);
    }

    /**
     * True when at least one deferral rule is configured. With none, enabling
     * search registers {@code tool_search} but defers nothing — harmless, and a
     * useful way to let a model discover the catalog without changing what is sent.
     */
    public boolean hasDeferralRules() {
        return !names.isEmpty() || !globs.isEmpty() || !sections.isEmpty() || !sources.isEmpty();
    }

    /**
     * Whether a tool matches any configured deferral rule.
     *
     * @param name    tool name
     * @param section tool section
     * @param source  tool source
     */
    public boolean matches(String name, String section, String source) {
        if (name != null && names.contains(name)) return true;
        if (section != null && sections.contains(section)) return true;
        if (source != null && sources.contains(source)) return true;
        if (name != null) {
            for (String glob : globs) {
                if (globMatches(glob, name)) return true;
            }
        }
        return false;
    }

    /**
     * Minimal glob matching supporting {@code *} (any run) and {@code ?} (one
     * character). Implemented by translating to a regex with everything else
     * quoted, so a tool named {@code a.b} cannot be matched by an unescaped dot.
     */
    static boolean globMatches(String glob, String value) {
        if (glob == null || value == null) return false;
        StringBuilder regex = new StringBuilder(glob.length() + 8);
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' || c == '?') {
                if (literal.length() > 0) {
                    regex.append(java.util.regex.Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0) regex.append(java.util.regex.Pattern.quote(literal.toString()));
        try {
            return value.matches(regex.toString());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
