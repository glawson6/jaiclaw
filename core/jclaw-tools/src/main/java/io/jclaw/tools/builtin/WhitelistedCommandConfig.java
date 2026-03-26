package io.jclaw.tools.builtin;

import io.jclaw.core.tool.ToolProfile;
import io.jclaw.tools.ToolCatalog;

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
}
