package io.jclaw.core.skill;

public record SkillDefinition(
        String name,
        String description,
        String content,
        SkillMetadata metadata
) {
}
