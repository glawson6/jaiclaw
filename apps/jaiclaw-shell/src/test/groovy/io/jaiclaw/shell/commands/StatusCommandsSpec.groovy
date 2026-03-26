package io.jaiclaw.shell.commands

import io.jaiclaw.agent.session.SessionManager
import io.jaiclaw.config.*
import io.jaiclaw.plugin.PluginRegistry
import io.jaiclaw.skills.SkillLoader
import io.jaiclaw.tools.ToolRegistry
import io.jaiclaw.core.skill.SkillDefinition
import io.jaiclaw.core.skill.SkillMetadata
import spock.lang.Specification

class StatusCommandsSpec extends Specification {

    ToolRegistry toolRegistry = new ToolRegistry()
    PluginRegistry pluginRegistry = new PluginRegistry()
    SessionManager sessionManager = new SessionManager()
    SkillLoader skillLoader = Mock()

    JaiClawProperties properties = new JaiClawProperties(
            null, null, null, null, null, null, null, null, null, null)

    StatusCommands commands = new StatusCommands(
            properties, toolRegistry, pluginRegistry, sessionManager, skillLoader)

    def "status shows system info"() {
        when:
        def result = commands.status()

        then:
        result.contains("JaiClaw Status")
        result.contains("Identity:")
        result.contains("Tools:")
        result.contains("Plugins:")
    }

    def "tools returns empty message when no tools"() {
        expect:
        commands.tools() == "No tools registered yet."
    }

    def "plugins returns empty message when no plugins"() {
        expect:
        commands.plugins() == "No plugins loaded."
    }

    def "skills returns empty message when no skills loaded"() {
        given:
        skillLoader.loadBundled() >> []

        expect:
        commands.skills() == "No skills loaded."
    }

    def "skills lists loaded skills"() {
        given:
        def alwaysOn = new SkillMetadata(true, "", Set.of(), Set.of())
        skillLoader.loadBundled() >> [
                new SkillDefinition("coding", "Code assistance", "content", alwaysOn),
                new SkillDefinition("web-research", "Web research", "content", SkillMetadata.EMPTY),
        ]

        when:
        def result = commands.skills()

        then:
        result.contains("Loaded Skills:")
        result.contains("coding")
        result.contains("web-research")
        result.contains("[always-include]")
    }

    def "config shows configuration properties"() {
        when:
        def result = commands.config()

        then:
        result.contains("JaiClaw Configuration")
        result.contains("Identity:")
        result.contains("Default Agent:")
        result.contains("Tool Profile:")
        result.contains("Memory Backend:")
    }

    def "models shows provider status"() {
        when:
        def result = commands.models()

        then:
        result.contains("LLM Providers:")
        result.contains("OpenAI")
        result.contains("Anthropic")
        result.contains("Ollama")
    }
}
