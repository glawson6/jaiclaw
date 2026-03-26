package io.jaiclaw.agent

import io.jaiclaw.core.model.AgentIdentity
import io.jaiclaw.core.skill.SkillDefinition
import io.jaiclaw.core.skill.SkillMetadata
import io.jaiclaw.core.tool.ToolCallback
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolDefinition
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
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

    def "includes tools section grouped by section"() {
        given:
        def tools = [
            stubTool("file_read", "Read a file", "Files"),
            stubTool("shell_exec", "Execute shell command", "Execution"),
        ]

        when:
        def prompt = builder.tools(tools).build()

        then:
        prompt.contains("# Available Tools")
        prompt.contains("## Execution")
        prompt.contains("## Files")
        prompt.contains("**file_read**")
        prompt.contains("**shell_exec**")
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

    private ToolCallback stubTool(String name, String desc, String section) {
        def definition = new ToolDefinition(name, desc, section,
            '{"type":"object","properties":{},"required":[]}',
            Set.of(ToolProfile.FULL))
        return new ToolCallback() {
            @Override
            ToolDefinition definition() { return definition }

            @Override
            ToolResult execute(Map<String, Object> parameters, ToolContext context) {
                return new ToolResult.Success("ok")
            }
        }
    }
}
