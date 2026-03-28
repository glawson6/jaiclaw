package io.jaiclaw.tools.builtin;

import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.tools.ToolCatalog;

import java.util.List;
import java.util.Set;

/**
 * Configuration for {@link WhitelistedCommandTool} and {@link ShowAllowedCommandsTool}.
 *
 * <p>Defines the allowed command prefixes, execution limits, and tool metadata.
 * This record is intended to be constructed by the application (e.g., from Spring
 * {@code @ConfigurationProperties}) and passed to the tool constructors.
 *
 * @param allowedPrefixes shell command prefixes that are permitted (whitelist)
 * @param timeoutSeconds  default command execution timeout
 * @param maxOutputLines  maximum output lines to capture
 * @param toolName        name for the command tool (default: "whitelisted_command")
 * @param section         tool catalog section (default: {@link ToolCatalog#SECTION_EXEC})
 * @param profiles        tool profiles (default: FULL only)
 */
public record WhitelistedCommandConfig(
        List<String> allowedPrefixes,
        int timeoutSeconds,
        int maxOutputLines,
        String toolName,
        String section,
        Set<ToolProfile> profiles
) {
    public WhitelistedCommandConfig {
        if (allowedPrefixes == null) allowedPrefixes = List.of();
        if (timeoutSeconds <= 0) timeoutSeconds = 60;
        if (maxOutputLines <= 0) maxOutputLines = 500;
        if (toolName == null || toolName.isBlank()) toolName = "whitelisted_command";
        if (section == null || section.isBlank()) section = ToolCatalog.SECTION_EXEC;
        if (profiles == null || profiles.isEmpty()) profiles = Set.of(ToolProfile.FULL);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private List<String> allowedPrefixes;
        private int timeoutSeconds;
        private int maxOutputLines;
        private String toolName;
        private String section;
        private Set<ToolProfile> profiles;

        public Builder allowedPrefixes(List<String> allowedPrefixes) { this.allowedPrefixes = allowedPrefixes; return this; }
        public Builder timeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; return this; }
        public Builder maxOutputLines(int maxOutputLines) { this.maxOutputLines = maxOutputLines; return this; }
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder section(String section) { this.section = section; return this; }
        public Builder profiles(Set<ToolProfile> profiles) { this.profiles = profiles; return this; }

        public WhitelistedCommandConfig build() {
            return new WhitelistedCommandConfig(
                    allowedPrefixes, timeoutSeconds, maxOutputLines, toolName, section, profiles);
        }
    }
}
