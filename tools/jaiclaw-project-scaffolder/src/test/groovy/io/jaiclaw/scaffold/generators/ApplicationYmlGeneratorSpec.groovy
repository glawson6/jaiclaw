package io.jaiclaw.scaffold.generators

import io.jaiclaw.scaffold.ProjectManifest
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLFactory
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

    def "does not generate Embabel exclusion for any archetype"() {
        given:
        def manifest = loadManifest("minimal.yml")

        when:
        def yml = ApplicationYmlGenerator.generate(manifest)

        then:
        !yml.contains("AgentPlatformAutoConfiguration")
    }

    def "generates Embabel config for embabel archetype"() {
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

    // --- ANTHROPIC_BASE_URL / OPENAI_BASE_URL placeholders (regression lock
    // for scaffolder-boot4-jaiclaw1-refresh). Adopters routing MiniMax or
    // any Anthropic-compatible proxy shouldn't have to edit the scaffolded
    // yaml — the env placeholder makes it a boot-time flip.

    def "anthropic provider block emits ANTHROPIC_BASE_URL placeholder"() {
        given:
        def manifest = loadManifest("minimal.yml")

        when:
        def yml = ApplicationYmlGenerator.generate(manifest)

        then:
        yml.contains('base-url: ${ANTHROPIC_BASE_URL:https://api.anthropic.com}')
    }

    def "openai provider block emits OPENAI_BASE_URL placeholder"() {
        given: "the personal-assistant fixture ships both anthropic + openai"
        def manifest = loadManifest("personal-assistant.yml")

        when:
        def yml = ApplicationYmlGenerator.generate(manifest)

        then:
        yml.contains('base-url: ${OPENAI_BASE_URL:https://api.openai.com}')
    }

    private ProjectManifest loadManifest(String name) {
        def stream = getClass().getResourceAsStream("/manifests/" + name)
        def map = yamlMapper.readValue(stream, Map)
        // Mirror PomGeneratorSpec: mimic ScaffoldMojo's default substitution
        // so fixture manifests stay minimal but generation never sees null
        // version fields.
        ProjectManifest manifest = ProjectManifest.fromYamlMap(map)
        if (manifest.jaiclawVersion() == null) {
            manifest = manifest.withJaiclawVersion("1.0.0-SNAPSHOT")
        }
        if (manifest.springBootVersion() == null) {
            manifest = manifest.withSpringBootVersion("4.1.0")
        }
        manifest
    }
}
