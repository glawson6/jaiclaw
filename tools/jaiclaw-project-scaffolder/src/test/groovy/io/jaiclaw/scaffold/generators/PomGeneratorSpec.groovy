package io.jaiclaw.scaffold.generators

import io.jaiclaw.scaffold.ProjectManifest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import spock.lang.Specification

class PomGeneratorSpec extends Specification {

    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())

    def "generates valid POM for gateway archetype"() {
        given:
        def manifest = loadManifest("minimal.yml")

        when:
        def pom = PomGenerator.generate(manifest)

        then:
        pom.contains("<artifactId>spring-boot-starter-parent</artifactId>")
        pom.contains("<artifactId>jaiclaw-bom</artifactId>")
        pom.contains("<artifactId>minimal-bot</artifactId>")
        pom.contains("<artifactId>jaiclaw-spring-boot-starter</artifactId>")
        pom.contains("<artifactId>jaiclaw-gateway</artifactId>")
        pom.contains("<artifactId>spring-boot-starter-web</artifactId>")
        pom.contains("<artifactId>spring-ai-starter-model-anthropic</artifactId>")
        pom.contains("<java.version>21</java.version>")
    }

    def "generates POM with extensions and channels"() {
        given:
        def manifest = loadManifest("helpdesk.yml")

        when:
        def pom = PomGenerator.generate(manifest)

        then:
        pom.contains("<artifactId>jaiclaw-security</artifactId>")
        pom.contains("<artifactId>jaiclaw-plugin-sdk</artifactId>")
        pom.contains("<artifactId>jaiclaw-channel-telegram</artifactId>")
        pom.contains("<artifactId>jaiclaw-channel-slack</artifactId>")
    }

    def "generates POM for camel archetype"() {
        given:
        def manifest = loadManifest("pdf-summarizer.yml")

        when:
        def pom = PomGenerator.generate(manifest)

        then:
        pom.contains("<artifactId>jaiclaw-camel</artifactId>")
        pom.contains("<artifactId>jaiclaw-documents</artifactId>")
    }

    def "generates POM for embabel archetype"() {
        given:
        def manifest = loadManifest("research-planner.yml")

        when:
        def pom = PomGenerator.generate(manifest)

        then:
        pom.contains("<artifactId>jaiclaw-embabel-delegate</artifactId>")
        pom.contains("<artifactId>spring-ai-starter-model-vertex-ai</artifactId>")
    }

    def "generates POM for comprehensive archetype with multiple providers"() {
        given:
        def manifest = loadManifest("personal-assistant.yml")

        when:
        def pom = PomGenerator.generate(manifest)

        then:
        pom.contains("<artifactId>jaiclaw-security</artifactId>")
        pom.contains("<artifactId>jaiclaw-documents</artifactId>")
        pom.contains("<artifactId>jaiclaw-media</artifactId>")
        pom.contains("<artifactId>spring-ai-starter-model-anthropic</artifactId>")
        pom.contains("<artifactId>spring-ai-starter-model-openai</artifactId>")
        pom.contains("<artifactId>spring-ai-starter-model-ollama</artifactId>")
        pom.contains("<groupId>io.mycompany</groupId>")
    }

    def "includes JKube profile when docker enabled"() {
        given:
        def manifest = loadManifest("minimal.yml")

        when:
        def pom = PomGenerator.generate(manifest)

        then:
        pom.contains("kubernetes-maven-plugin")
        pom.contains("eclipse-temurin:21-jre")
    }

    def "excludes JKube profile when docker disabled"() {
        given:
        def manifest = loadManifest("research-planner.yml")

        when:
        def pom = PomGenerator.generate(manifest)

        then:
        !pom.contains("kubernetes-maven-plugin")
    }

    def "includes test dependencies in standalone mode"() {
        given:
        def manifest = loadManifest("minimal.yml")

        when:
        def pom = PomGenerator.generate(manifest)

        then:
        pom.contains("spring-boot-starter-test")
        pom.contains("spock-core")
        pom.contains("groovy")
        pom.contains("gmavenplus-plugin")
    }

    // --- JAICLAW parent mode tests ---

    def "generates POM with jaiclaw-parent for jaiclaw mode"() {
        given:
        def manifest = loadManifest("minimal-jaiclaw.yml")

        when:
        def pom = PomGenerator.generate(manifest)

        then: "uses jaiclaw-parent instead of spring-boot-starter-parent"
        pom.contains("<artifactId>jaiclaw-parent</artifactId>")
        pom.contains("<groupId>io.jaiclaw</groupId>")
        !pom.contains("<artifactId>spring-boot-starter-parent</artifactId>")

        and: "no dependencyManagement block (inherited from parent)"
        !pom.contains("<dependencyManagement>")
        !pom.contains("jaiclaw-bom")

        and: "no properties block (inherited from parent)"
        !pom.contains("<java.version>")

        and: "no groupId on project (inherited from parent)"
        !pom.contains("<groupId>com.example</groupId>")

        and: "still has core dependencies"
        pom.contains("<artifactId>jaiclaw-spring-boot-starter</artifactId>")
        pom.contains("<artifactId>jaiclaw-gateway</artifactId>")
        pom.contains("<artifactId>spring-ai-starter-model-anthropic</artifactId>")
    }

    def "jaiclaw mode POM has lean build section"() {
        given:
        def manifest = loadManifest("minimal-jaiclaw.yml")

        when:
        def pom = PomGenerator.generate(manifest)

        then: "no gmavenplus or surefire config (inherited from parent)"
        !pom.contains("gmavenplus-plugin")
        !pom.contains("maven-surefire-plugin")

        and: "no explicit test dependency versions (managed by parent)"
        !pom.contains("spock-core")
        !pom.contains("<artifactId>groovy</artifactId>")

        and: "includes jaiclaw-maven-plugin with project.version"
        pom.contains("jaiclaw-maven-plugin")
        pom.contains('${project.version}')
        pom.contains("<goal>analyze</goal>")
    }

    def "jaiclaw mode POM omits JKube profile"() {
        given:
        def manifest = loadManifest("minimal-jaiclaw.yml")

        when:
        def pom = PomGenerator.generate(manifest)

        then: "no JKube profile (inherited from jaiclaw-parent)"
        !pom.contains("kubernetes-maven-plugin")
        !pom.contains("<profiles>")
    }

    def "jaiclaw mode POM has extensions and channels"() {
        given:
        def manifest = loadManifest("helpdesk-jaiclaw.yml")

        when:
        def pom = PomGenerator.generate(manifest)

        then:
        pom.contains("<artifactId>jaiclaw-parent</artifactId>")
        pom.contains("<artifactId>jaiclaw-security</artifactId>")
        pom.contains("<artifactId>jaiclaw-plugin-sdk</artifactId>")
        pom.contains("<artifactId>jaiclaw-channel-telegram</artifactId>")
        pom.contains("<artifactId>jaiclaw-channel-slack</artifactId>")
    }

    private ProjectManifest loadManifest(String name) {
        def stream = getClass().getResourceAsStream("/manifests/" + name)
        def map = yamlMapper.readValue(stream, Map)
        ProjectManifest.fromYamlMap(map)
    }
}
