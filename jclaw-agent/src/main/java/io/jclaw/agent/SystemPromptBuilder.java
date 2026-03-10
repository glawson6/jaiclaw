package io.jclaw.agent;

import io.jclaw.core.model.AgentIdentity;
import io.jclaw.core.skill.SkillDefinition;
import io.jclaw.core.tool.ToolCallback;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Constructs the system prompt from identity, skills, tools, and session context.
 */
public class SystemPromptBuilder {

    private AgentIdentity identity = AgentIdentity.DEFAULT;
    private List<SkillDefinition> skills = List.of();
    private Collection<? extends ToolCallback> tools = List.of();
    private String additionalInstructions = "";

    public SystemPromptBuilder identity(AgentIdentity identity) {
        this.identity = identity;
        return this;
    }

    public SystemPromptBuilder skills(List<SkillDefinition> skills) {
        this.skills = skills;
        return this;
    }

    public SystemPromptBuilder tools(Collection<? extends ToolCallback> tools) {
        this.tools = tools;
        return this;
    }

    public SystemPromptBuilder additionalInstructions(String instructions) {
        this.additionalInstructions = instructions;
        return this;
    }

    public String build() {
        var sb = new StringBuilder();

        // Identity section
        sb.append("You are ").append(identity.name());
        if (!identity.description().isBlank()) {
            sb.append(", ").append(identity.description());
        }
        sb.append(".\n\n");

        // Date context
        sb.append("Today's date is ").append(LocalDate.now()).append(".\n\n");

        // Skills section
        if (!skills.isEmpty()) {
            sb.append("# Skills\n\n");
            sb.append("You have the following skills available:\n\n");
            for (var skill : skills) {
                sb.append("## ").append(skill.name()).append("\n");
                if (!skill.description().isBlank()) {
                    sb.append(skill.description()).append("\n");
                }
                sb.append('\n');
                sb.append(skill.content()).append("\n\n");
            }
        }

        // Tools section
        if (!tools.isEmpty()) {
            sb.append("# Available Tools\n\n");
            String currentSection = "";
            var sorted = tools.stream()
                    .sorted((a, b) -> a.definition().section().compareTo(b.definition().section()))
                    .toList();
            for (var tool : sorted) {
                var def = tool.definition();
                if (!def.section().equals(currentSection)) {
                    currentSection = def.section();
                    sb.append("## ").append(currentSection).append("\n\n");
                }
                sb.append("- **").append(def.name()).append("**: ").append(def.description()).append('\n');
            }
            sb.append('\n');
        }

        // Additional instructions
        if (!additionalInstructions.isBlank()) {
            sb.append(additionalInstructions).append('\n');
        }

        return sb.toString();
    }
}
