package io.jaiclaw.agent

import io.jaiclaw.core.model.AgentIdentity
import io.jaiclaw.core.skill.SkillDefinition
import io.jaiclaw.core.skill.SkillMetadata
import spock.lang.Specification

class SystemPromptBuilderSpec extends Specification {

    SystemPromptBuilder builder = new SystemPromptBuilder()

    def "builds prompt with identity"() {
        when:
        def prompt = builder
            .identity(new AgentIdentity("test", "TestBot", "a helpful bot"))
            .build()

        then:
        prompt.contains("You are TestBot")
        prompt.contains("a helpful bot")
    }

    def "builds prompt with default identity"() {
        when:
        def prompt = builder.build()

        then:
        prompt.contains("You are JaiClaw")
    }

    def "includes today's date"() {
        when:
        def prompt = builder.build()

        then:
        prompt.contains("Today's date is")
    }

    def "includes skills section"() {
        given:
        def skills = [
            new SkillDefinition("git-commit", "Helps with git commits", "Use git commit -m ...", SkillMetadata.EMPTY),
            new SkillDefinition("code-review", "Reviews code", "Review the code for...", SkillMetadata.EMPTY),
        ]

        when:
        def prompt = builder.skills(skills).build()

        then:
        prompt.contains("# Skills")
        prompt.contains("## git-commit")
        prompt.contains("Helps with git commits")
        prompt.contains("Use git commit -m")
        prompt.contains("## code-review")
    }

    def "includes additional instructions"() {
        when:
        def prompt = builder
            .additionalInstructions("Always be polite.")
            .build()

        then:
        prompt.contains("Always be polite.")
    }

    def "empty skills and tools produce no sections"() {
        when:
        def prompt = builder.build()

        then:
        !prompt.contains("# Skills")
        !prompt.contains("# Available Tools")
    }
}
