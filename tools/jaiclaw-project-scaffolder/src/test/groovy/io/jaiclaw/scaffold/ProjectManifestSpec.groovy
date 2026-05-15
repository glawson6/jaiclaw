package io.jaiclaw.scaffold

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import spock.lang.Specification

class ProjectManifestSpec extends Specification {

    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())

    def "parses minimal manifest with just name and description"() {
        given:
        def yaml = loadManifest("minimal.yml")

        when:
        def manifest = ProjectManifest.fromYamlMap(yaml)

        then:
        manifest.name() == "minimal-bot"
        manifest.description() == "A minimal JaiClaw bot with all defaults"
        manifest.groupId() == "com.example"
        manifest.javaPackage() == "com.example.minimalbot"
        manifest.version() == "0.1.0-SNAPSHOT"
        manifest.archetype() == ProjectManifest.Archetype.GATEWAY
        manifest.aiProvider().primary() == "anthropic"
        manifest.aiProvider().additional().isEmpty()
        manifest.extensions().isEmpty()
        manifest.channels().isEmpty()
        manifest.skills().allowBundled().isEmpty()
        manifest.security().mode() == "api-key"
        manifest.customTools().isEmpty()
    }

    def "parses full helpdesk manifest"() {
        given:
        def yaml = loadManifest("helpdesk.yml")

        when:
        def manifest = ProjectManifest.fromYamlMap(yaml)

        then:
        manifest.name() == "helpdesk-bot"
        manifest.extensions() == ["security", "plugin-sdk"]
        manifest.channels() == ["telegram", "slack"]
        manifest.agent().name() == "Helpdesk Agent"
        manifest.agent().toolsProfile() == "full"
        manifest.agent().systemPrompt().strategy() == "classpath"
        manifest.agent().systemPrompt().source() == "prompts/system-prompt.md"
        manifest.customTools().size() == 1
        manifest.customTools()[0].name() == "search_faq"
        manifest.customTools()[0].parameters().containsKey("question")
        manifest.customTools()[0].parameters().get("question").required()
    }

    def "parses camel manifest"() {
        given:
        def yaml = loadManifest("pdf-summarizer.yml")

        when:
        def manifest = ProjectManifest.fromYamlMap(yaml)

        then:
        manifest.archetype() == ProjectManifest.Archetype.CAMEL
        manifest.camel() != null
        manifest.camel().channelId() == "pdf-summarizer"
        manifest.camel().stateless()
    }

    def "parses embabel manifest"() {
        given:
        def yaml = loadManifest("research-planner.yml")

        when:
        def manifest = ProjectManifest.fromYamlMap(yaml)

        then:
        manifest.archetype() == ProjectManifest.Archetype.EMBABEL
        manifest.embabel() != null
        manifest.embabel().defaultLlm() == "claude-sonnet-4-5"
        manifest.embabel().workflow() == "ResearchPlannerAgent"
        !manifest.docker().enabled()
    }

    def "parses comprehensive manifest with all fields"() {
        given:
        def yaml = loadManifest("personal-assistant.yml")

        when:
        def manifest = ProjectManifest.fromYamlMap(yaml)

        then:
        manifest.archetype() == ProjectManifest.Archetype.COMPREHENSIVE
        manifest.groupId() == "io.mycompany"
        manifest.javaPackage() == "io.mycompany.assistant"
        manifest.version() == "1.0.0-SNAPSHOT"
        manifest.aiProvider().primary() == "anthropic"
        manifest.aiProvider().additional() == ["openai", "ollama"]
        manifest.channels() == ["telegram", "slack", "discord"]
        manifest.server().port() == 9090
        manifest.security().mode() == "jwt"
        manifest.readme().problem().contains("daily tasks")
    }

    def "validates missing name"() {
        given:
        def manifest = ProjectManifest.builder()
                .name(null)
                .description("test")
                .build()

        when:
        manifest.validate()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("name")
    }

    def "validates invalid name format"() {
        given:
        def manifest = ProjectManifest.builder()
                .name("InvalidName")
                .description("test")
                .build()

        when:
        manifest.validate()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("kebab-case")
    }

    def "validates missing description"() {
        given:
        def manifest = ProjectManifest.builder()
                .name("test-bot")
                .description(null)
                .build()

        when:
        manifest.validate()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("description")
    }

    def "validates unknown extension"() {
        given:
        def manifest = ProjectManifest.builder()
                .name("test-bot")
                .description("test")
                .extensions(["nonexistent"])
                .build()

        when:
        manifest.validate()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unknown extension")
    }

    def "validates unknown channel"() {
        given:
        def manifest = ProjectManifest.builder()
                .name("test-bot")
                .description("test")
                .channels(["whatsapp"])
                .build()

        when:
        manifest.validate()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unknown channel")
    }

    def "validates unknown AI provider"() {
        given:
        def manifest = ProjectManifest.builder()
                .name("test-bot")
                .description("test")
                .aiProvider(new ProjectManifest.AiProvider("unknown-ai", []))
                .build()

        when:
        manifest.validate()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unknown primary AI provider")
    }

    def "validates camel archetype requires camel config"() {
        given:
        def manifest = ProjectManifest.builder()
                .name("test-bot")
                .description("test")
                .archetype(ProjectManifest.Archetype.CAMEL)
                .build()

        when:
        manifest.validate()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("camel")
    }

    def "validates embabel archetype requires embabel config"() {
        given:
        def manifest = ProjectManifest.builder()
                .name("test-bot")
                .description("test")
                .archetype(ProjectManifest.Archetype.EMBABEL)
                .build()

        when:
        manifest.validate()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("embabel")
    }

    def "defaults to STANDALONE parent mode"() {
        given:
        def yaml = loadManifest("minimal.yml")

        when:
        def manifest = ProjectManifest.fromYamlMap(yaml)

        then:
        manifest.parentMode() == ProjectManifest.ParentMode.STANDALONE
    }

    def "parses jaiclaw parent mode"() {
        given:
        def yaml = loadManifest("minimal-jaiclaw.yml")

        when:
        def manifest = ProjectManifest.fromYamlMap(yaml)

        then:
        manifest.parentMode() == ProjectManifest.ParentMode.JAICLAW
    }

    def "builder defaults to STANDALONE parent mode"() {
        given:
        def manifest = ProjectManifest.builder()
                .name("test-bot")
                .description("test")
                .build()

        expect:
        manifest.parentMode() == ProjectManifest.ParentMode.STANDALONE
    }

    def "builder accepts JAICLAW parent mode"() {
        given:
        def manifest = ProjectManifest.builder()
                .name("test-bot")
                .description("test")
                .parentMode(ProjectManifest.ParentMode.JAICLAW)
                .build()

        expect:
        manifest.parentMode() == ProjectManifest.ParentMode.JAICLAW
    }

    def "derives applicationClassName correctly"() {
        expect:
        ProjectManifest.toPascalCase("helpdesk-bot") == "HelpdeskBot"
        ProjectManifest.toPascalCase("pdf-summarizer") == "PdfSummarizer"
        ProjectManifest.toPascalCase("my-cool-app") == "MyCoolApp"
        ProjectManifest.toPascalCase("simple") == "Simple"
    }

    def "derives packagePath correctly"() {
        given:
        def manifest = ProjectManifest.builder()
                .name("test-bot")
                .description("test")
                .groupId("com.example")
                .javaPackage("com.example.testbot")
                .build()

        expect:
        manifest.packagePath() == "com/example/testbot"
    }

    def "valid manifest passes validation"() {
        given:
        def yaml = loadManifest("helpdesk.yml")
        def manifest = ProjectManifest.fromYamlMap(yaml)

        when:
        manifest.validate()

        then:
        noExceptionThrown()
    }

    private Map<String, Object> loadManifest(String name) {
        def stream = getClass().getResourceAsStream("/manifests/" + name)
        yamlMapper.readValue(stream, Map)
    }
}
