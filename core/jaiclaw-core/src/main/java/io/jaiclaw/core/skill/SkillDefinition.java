package io.jaiclaw.core.skill;

public record SkillDefinition(
        String name,
        String description,
        String content,
        SkillMetadata metadata
) {
}
