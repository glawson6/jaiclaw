package io.jclaw.skills;

import io.jclaw.core.skill.SkillDefinition;

import java.util.List;

/**
 * Builds the skill section of the agent system prompt from loaded skill definitions.
 */
public class SkillPromptBuilder {

    /**
     * Build the skills section for injection into the system prompt.
     * Only includes skills whose {@code alwaysInclude} flag is set.
     */
    public String buildAlwaysIncludeSection(List<SkillDefinition> skills) {
        var included = skills.stream()
                .filter(s -> s.metadata().alwaysInclude())
                .toList();

        if (included.isEmpty()) return "";

        var sb = new StringBuilder("# Active Skills\n\n");
        for (var skill : included) {
            sb.append("## ").append(skill.name()).append('\n');
            if (!skill.description().isBlank()) {
                sb.append(skill.description()).append('\n');
            }
            sb.append('\n').append(skill.content()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * Build a summary of all available skills (for /skills command listing).
     */
    public String buildSkillSummary(List<SkillDefinition> skills) {
        if (skills.isEmpty()) return "No skills loaded.";

        var sb = new StringBuilder();
        for (var skill : skills) {
            sb.append("- **").append(skill.name()).append("**");
            if (!skill.description().isBlank()) {
                sb.append(": ").append(skill.description());
            }
            if (skill.metadata().alwaysInclude()) {
                sb.append(" [always-include]");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
