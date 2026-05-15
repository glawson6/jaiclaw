package io.jaiclaw.scaffold

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class ProjectGeneratorSpec extends Specification {

    @TempDir
    Path tempDir

    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
    ProjectGenerator generator = new ProjectGenerator()

    def "generates complete project from minimal manifest"() {
        given:
        def manifest = loadManifest("minimal.yml")

        when:
        def projectDir = generator.generate(manifest, tempDir)

        then: "project directory is created"
        Files.exists(projectDir)
        projectDir.fileName.toString() == "minimal-bot"

        and: "pom.xml exists and is valid XML"
        def pomFile = projectDir.resolve("pom.xml")
        Files.exists(pomFile)
        def pomContent = Files.readString(pomFile)
        pomContent.contains("<artifactId>minimal-bot</artifactId>")
        pomContent.contains("jaiclaw-bom")

        and: "application.yml exists"
        def ymlFile = projectDir.resolve("src/main/resources/application.yml")
        Files.exists(ymlFile)

        and: "main class exists"
        def mainClass = projectDir.resolve("src/main/java/com/example/minimalbot/MinimalBotApplication.java")
        Files.exists(mainClass)
        def mainContent = Files.readString(mainClass)
        mainContent.contains("@SpringBootApplication")
        mainContent.contains("MinimalBotApplication")

        and: "test directory exists"
        Files.exists(projectDir.resolve("src/test/groovy/com/example/minimalbot"))

        and: "README exists"
        Files.exists(projectDir.resolve("README.md"))
    }

    def "generates project with custom tools"() {
        given:
        def manifest = loadManifest("helpdesk.yml")

        when:
        def projectDir = generator.generate(manifest, tempDir)

        then: "tool stub is generated"
        def toolFile = projectDir.resolve("src/main/java/com/example/helpdeskbot/SearchFaqTool.java")
        Files.exists(toolFile)
        def content = Files.readString(toolFile)
        content.contains("@Component")
        content.contains("implements ToolCallback")
        content.contains("search_faq")
        content.contains("question")
    }

    def "generates project with classpath system prompt"() {
        given:
        def manifest = loadManifest("helpdesk.yml")

        when:
        def projectDir = generator.generate(manifest, tempDir)

        then:
        def promptFile = projectDir.resolve("src/main/resources/prompts/system-prompt.md")
        Files.exists(promptFile)
        def content = Files.readString(promptFile)
        content.contains("Helpdesk Agent")
    }

    def "generates project for comprehensive archetype"() {
        given:
        def manifest = loadManifest("personal-assistant.yml")

        when:
        def projectDir = generator.generate(manifest, tempDir)

        then:
        def pomContent = Files.readString(projectDir.resolve("pom.xml"))
        pomContent.contains("<groupId>io.mycompany</groupId>")
        pomContent.contains("jaiclaw-security")
        pomContent.contains("jaiclaw-documents")
        pomContent.contains("jaiclaw-channel-telegram")
        pomContent.contains("jaiclaw-channel-slack")
        pomContent.contains("jaiclaw-channel-discord")

        and: "main class uses custom package"
        def mainClass = projectDir.resolve("src/main/java/io/mycompany/assistant/PersonalAssistantApplication.java")
        Files.exists(mainClass)

        and: "application.yml has correct port"
        def ymlContent = Files.readString(projectDir.resolve("src/main/resources/application.yml"))
        ymlContent.contains("9090")
    }

    def "generates project for camel archetype"() {
        given:
        def manifest = loadManifest("pdf-summarizer.yml")

        when:
        def projectDir = generator.generate(manifest, tempDir)

        then:
        def pomContent = Files.readString(projectDir.resolve("pom.xml"))
        pomContent.contains("jaiclaw-camel")

        and:
        def ymlContent = Files.readString(projectDir.resolve("src/main/resources/application.yml"))
        ymlContent.contains("channel-id: pdf-summarizer")
    }

    def "generates project for embabel archetype"() {
        given:
        def manifest = loadManifest("research-planner.yml")

        when:
        def projectDir = generator.generate(manifest, tempDir)

        then:
        def pomContent = Files.readString(projectDir.resolve("pom.xml"))
        pomContent.contains("jaiclaw-embabel-delegate")

        and:
        def ymlContent = Files.readString(projectDir.resolve("src/main/resources/application.yml"))
        ymlContent.contains("default-llm")
        !ymlContent.contains("AgentPlatformAutoConfiguration")
    }

    def "generates project with jaiclaw parent mode"() {
        given:
        def manifest = loadManifest("helpdesk-jaiclaw.yml")

        when:
        def projectDir = generator.generate(manifest, tempDir)

        then: "pom.xml uses jaiclaw-parent"
        def pomContent = Files.readString(projectDir.resolve("pom.xml"))
        pomContent.contains("<artifactId>jaiclaw-parent</artifactId>")
        !pomContent.contains("<artifactId>spring-boot-starter-parent</artifactId>")
        !pomContent.contains("jaiclaw-bom")
        pomContent.contains("jaiclaw-maven-plugin")
        pomContent.contains("jaiclaw-security")
        pomContent.contains("jaiclaw-channel-telegram")

        and: "application.yml still generated"
        Files.exists(projectDir.resolve("src/main/resources/application.yml"))

        and: "main class still generated"
        def mainClass = projectDir.resolve("src/main/java/com/example/helpdeskbot/HelpdeskBotApplication.java")
        Files.exists(mainClass)

        and: "custom tool still generated"
        def toolFile = projectDir.resolve("src/main/java/com/example/helpdeskbot/SearchFaqTool.java")
        Files.exists(toolFile)

        and: "system prompt still generated"
        Files.exists(projectDir.resolve("src/main/resources/prompts/system-prompt.md"))
    }

    def "does not overwrite existing project directory"() {
        given:
        def manifest = loadManifest("minimal.yml")
        def projectDir = tempDir.resolve("minimal-bot")
        Files.createDirectories(projectDir)
        def existingFile = projectDir.resolve("existing.txt")
        Files.writeString(existingFile, "keep me")

        when:
        generator.generate(manifest, tempDir)

        then: "existing file is preserved"
        Files.exists(existingFile)
        Files.readString(existingFile) == "keep me"

        and: "new files are added"
        Files.exists(projectDir.resolve("pom.xml"))
    }

    private ProjectManifest loadManifest(String name) {
        def stream = getClass().getResourceAsStream("/manifests/" + name)
        def map = yamlMapper.readValue(stream, Map)
        ProjectManifest.fromYamlMap(map)
    }
}
