package io.jclaw.skills

import io.jclaw.core.skill.SkillDefinition
import io.jclaw.core.skill.SkillMetadata
import spock.lang.Specification

class SkillPromptBuilderSpec extends Specification {

    SkillPromptBuilder builder = new SkillPromptBuilder()

    def "buildAlwaysIncludeSection includes only alwaysInclude skills"() {
        given:
        def alwaysOn = new SkillMetadata(true, "", Set.of(), Set.of())
        def skills = [
            new SkillDefinition("always", "Always on", "Always content", alwaysOn),
            new SkillDefinition("optional", "Optional", "Optional content", SkillMetadata.EMPTY),
        ]

        when:
        def section = builder.buildAlwaysIncludeSection(skills)

        then:
        section.contains("## always")
        section.contains("Always content")
        !section.contains("## optional")
    }

    def "buildAlwaysIncludeSection returns empty when no alwaysInclude skills"() {
        given:
        def skills = [
            new SkillDefinition("opt", "desc", "content", SkillMetadata.EMPTY),
        ]

        expect:
        builder.buildAlwaysIncludeSection(skills) == ""
    }

    def "buildSkillSummary lists all skills"() {
        given:
        def alwaysOn = new SkillMetadata(true, "", Set.of(), Set.of())
        def skills = [
            new SkillDefinition("skill-a", "Does A", "content", SkillMetadata.EMPTY),
            new SkillDefinition("skill-b", "Does B", "content", alwaysOn),
        ]

        when:
        def summary = builder.buildSkillSummary(skills)

        then:
        summary.contains("**skill-a**")
        summary.contains("Does A")
        summary.contains("**skill-b**")
        summary.contains("[always-include]")
    }

    def "buildSkillSummary returns message when no skills"() {
        expect:
        builder.buildSkillSummary([]) == "No skills loaded."
    }
}
