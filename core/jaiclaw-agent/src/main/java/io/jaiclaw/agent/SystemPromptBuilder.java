package io.jaiclaw.agent;

import io.jaiclaw.core.model.AgentIdentity;
import io.jaiclaw.core.skill.SkillDefinition;

import java.time.LocalDate;
import java.util.List;

/**
 * Constructs the system prompt from identity, skills, tools, and session context.
 */
public class SystemPromptBuilder {

    private AgentIdentity identity = AgentIdentity.DEFAULT;
    private List<SkillDefinition> skills = List.of();
    private String additionalInstructions = "";

    public SystemPromptBuilder identity(AgentIdentity identity) {
        this.identity = identity;
        return this;
    }

    public SystemPromptBuilder skills(List<SkillDefinition> skills) {
        this.skills = skills;
        return this;
    }

    /**
     * @deprecated Tools are now sent as structured JSON schemas by Spring AI. This setter is a no-op.
     */
    @Deprecated
    public SystemPromptBuilder tools(Object tools) {
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

        // Core behavior
        sb.append("Respond directly to the user in natural language. ");
        sb.append("Never output your internal reasoning, chain-of-thought, or planning. ");
        sb.append("Your entire response is shown to the user as-is.\n\n");

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

        // Tools section omitted — Spring AI sends tool definitions as structured JSON schemas
        // in the API request. Duplicating them in the system prompt wastes tokens.

        // Additional instructions
        if (!additionalInstructions.isBlank()) {
            sb.append(additionalInstructions).append('\n');
        }

        return sb.toString();
    }
}
