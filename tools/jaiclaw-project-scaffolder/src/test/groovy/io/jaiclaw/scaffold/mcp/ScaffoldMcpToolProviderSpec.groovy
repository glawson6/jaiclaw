package io.jaiclaw.scaffold.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class ScaffoldMcpToolProviderSpec extends Specification {

    @TempDir
    Path tempDir

    ObjectMapper objectMapper = new ObjectMapper()
    ScaffoldMcpToolProvider provider = new ScaffoldMcpToolProvider(objectMapper)

    def "server name is project-scaffolder"() {
        expect:
        provider.getServerName() == "project-scaffolder"
    }

    def "exposes three tools"() {
        when:
        def tools = provider.getTools()

        then:
        tools.size() == 3
        tools*.name().containsAll(["scaffold_project", "validate_manifest", "list_options"])
    }

    def "scaffold_project generates a project"() {
        given:
        def manifestYaml = """
            name: test-bot
            description: A test bot
            """.stripIndent()

        when:
        def result = provider.execute("scaffold_project",
                [manifest_yaml: manifestYaml, output_dir: tempDir.toString()], null)

        then:
        !result.isError()
        def parsed = objectMapper.readValue(result.content(), Map)
        parsed.status == "success"
        parsed.artifact_id == "test-bot"
        parsed.archetype == "gateway"
        parsed.ai_provider == "anthropic"

        and: "project files exist"
        Files.exists(tempDir.resolve("test-bot/pom.xml"))
        Files.exists(tempDir.resolve("test-bot/src/main/resources/application.yml"))
        Files.exists(tempDir.resolve("test-bot/README.md"))
    }

    def "scaffold_project with custom tools"() {
        given:
        def manifestYaml = """
            name: tool-bot
            description: Bot with custom tools
            custom-tools:
              - name: do_something
                description: Does something
                section: custom
                parameters:
                  input: { type: string, description: "Input data", required: true }
            """.stripIndent()

        when:
        def result = provider.execute("scaffold_project",
                [manifest_yaml: manifestYaml, output_dir: tempDir.toString()], null)

        then:
        !result.isError()
        def parsed = objectMapper.readValue(result.content(), Map)
        parsed.custom_tools == ["do_something"]
        Files.exists(tempDir.resolve("tool-bot/src/main/java/com/example/toolbot/DoSomethingTool.java"))
    }

    def "scaffold_project fails on invalid manifest"() {
        given:
        def manifestYaml = """
            name: InvalidName
            description: Bad name
            """.stripIndent()

        when:
        def result = provider.execute("scaffold_project",
                [manifest_yaml: manifestYaml], null)

        then:
        result.isError()
        result.content().contains("Invalid manifest")
        result.content().contains("kebab-case")
    }

    def "scaffold_project fails on missing manifest_yaml"() {
        when:
        def result = provider.execute("scaffold_project", [:], null)

        then:
        result.isError()
        result.content().contains("manifest_yaml")
    }

    def "validate_manifest returns valid for good manifest"() {
        given:
        def manifestYaml = """
            name: valid-bot
            description: A valid bot
            channels: [telegram]
            extensions: [security]
            """.stripIndent()

        when:
        def result = provider.execute("validate_manifest",
                [manifest_yaml: manifestYaml], null)

        then:
        !result.isError()
        def parsed = objectMapper.readValue(result.content(), Map)
        parsed.valid == true
        parsed.name == "valid-bot"
        parsed.channels_count == 1
        parsed.extensions_count == 1
    }

    def "validate_manifest returns invalid with error message"() {
        given:
        def manifestYaml = """
            name: bad-bot
            description: A bot with bad channel
            channels: [whatsapp]
            """.stripIndent()

        when:
        def result = provider.execute("validate_manifest",
                [manifest_yaml: manifestYaml], null)

        then:
        !result.isError()
        def parsed = objectMapper.readValue(result.content(), Map)
        parsed.valid == false
        parsed.error.contains("Unknown channel")
    }

    def "validate_manifest returns invalid for YAML missing required fields"() {
        when:
        def result = provider.execute("validate_manifest",
                [manifest_yaml: "some-key: some-value"], null)

        then:
        !result.isError()
        def parsed = objectMapper.readValue(result.content(), Map)
        parsed.valid == false
        parsed.error.contains("name")
    }

    def "list_options returns extensions"() {
        when:
        def result = provider.execute("list_options",
                [category: "extensions"], null)

        then:
        !result.isError()
        def parsed = objectMapper.readValue(result.content(), Map)
        parsed.category == "extensions"
        parsed.options instanceof List
        (parsed.options as List).contains("security")
        (parsed.options as List).contains("documents")
        (parsed.options as List).contains("browser")
    }

    def "list_options returns channels"() {
        when:
        def result = provider.execute("list_options",
                [category: "channels"], null)

        then:
        !result.isError()
        def parsed = objectMapper.readValue(result.content(), Map)
        (parsed.options as List).containsAll(["telegram", "slack", "discord", "email", "sms", "signal", "teams"])
    }

    def "list_options returns providers"() {
        when:
        def result = provider.execute("list_options",
                [category: "providers"], null)

        then:
        !result.isError()
        def parsed = objectMapper.readValue(result.content(), Map)
        (parsed.options as List).containsAll(["anthropic", "openai", "ollama", "gemini"])
    }

    def "list_options returns archetypes"() {
        when:
        def result = provider.execute("list_options",
                [category: "archetypes"], null)

        then:
        !result.isError()
        def parsed = objectMapper.readValue(result.content(), Map)
        parsed.options == ["gateway", "embabel", "camel", "comprehensive", "minimal"]
    }

    def "list_options fails on unknown category"() {
        when:
        def result = provider.execute("list_options",
                [category: "nonexistent"], null)

        then:
        result.isError()
        result.content().contains("Unknown category")
    }

    def "unknown tool returns error"() {
        when:
        def result = provider.execute("nonexistent_tool", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }
}
