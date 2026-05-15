package io.jaiclaw.scaffold.generators

import io.jaiclaw.scaffold.ProjectManifest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import spock.lang.Specification

class ApplicationYmlGeneratorSpec extends Specification {

    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())

    def "generates valid YAML for minimal manifest"() {
        given:
        def manifest = loadManifest("minimal.yml")

        when:
        def yml = ApplicationYmlGenerator.generate(manifest)

        then:
        yml.contains("server:")
        yml.contains("port: \${GATEWAY_PORT:8080}")
        yml.contains("allow-bundled: []")
        yml.contains("profile: full")
        yml.contains("anthropic:")

        and: "generated YAML should parse back successfully"
        def parsed = yamlMapper.readValue(yml, Map)
        parsed.containsKey("server")
        parsed.containsKey("jaiclaw")
        parsed.containsKey("spring")
    }

    def "generates YAML with security config"() {
        given:
        def manifest = loadManifest("helpdesk.yml")

        when:
        def yml = ApplicationYmlGenerator.generate(manifest)

        then:
        yml.contains("mode: \${JAICLAW_SECURITY_MODE:api-key}")
        yml.contains("api-key: \${JAICLAW_API_KEY:}")
    }

    def "generates YAML with Embabel exclusion for non-embabel archetypes"() {
        given:
        def manifest = loadManifest("minimal.yml")

        when:
        def yml = ApplicationYmlGenerator.generate(manifest)

        then:
        yml.contains("AgentPlatformAutoConfiguration")
    }

    def "generates YAML without Embabel exclusion for embabel archetype"() {
        given:
        def manifest = loadManifest("research-planner.yml")

        when:
        def yml = ApplicationYmlGenerator.generate(manifest)

        then:
        !yml.contains("AgentPlatformAutoConfiguration")
        yml.contains("embabel:")
        yml.contains("default-llm:")
    }

    def "generates YAML with camel config"() {
        given:
        def manifest = loadManifest("pdf-summarizer.yml")

        when:
        def yml = ApplicationYmlGenerator.generate(manifest)

        then:
        yml.contains("channel-id: pdf-summarizer")
        yml.contains("stateless: true")
    }

    def "generates YAML with multiple AI providers"() {
        given:
        def manifest = loadManifest("personal-assistant.yml")

        when:
        def yml = ApplicationYmlGenerator.generate(manifest)

        then:
        yml.contains("anthropic:")
        yml.contains("openai:")
        yml.contains("ollama:")
        yml.contains("port: \${GATEWAY_PORT:9090}")
    }

    def "generates YAML with inline system prompt"() {
        given:
        def manifest = loadManifest("personal-assistant.yml")

        when:
        def yml = ApplicationYmlGenerator.generate(manifest)

        then:
        yml.contains("system-prompt:")
        yml.contains("content: |")
    }

    def "omits security section when mode is none"() {
        given:
        def manifest = loadManifest("pdf-summarizer.yml")

        when:
        def yml = ApplicationYmlGenerator.generate(manifest)

        then:
        !yml.contains("JAICLAW_SECURITY_MODE")
    }

    private ProjectManifest loadManifest(String name) {
        def stream = getClass().getResourceAsStream("/manifests/" + name)
        def map = yamlMapper.readValue(stream, Map)
        ProjectManifest.fromYamlMap(map)
    }
}
