package io.jclaw.shell.commands.setup.config

import io.jclaw.shell.commands.setup.OnboardResult
import spock.lang.Specification

import java.nio.file.Path

class YamlConfigWriterSpec extends Specification {

    YamlConfigWriter writer = new YamlConfigWriter()

    def "generates YAML for OpenAI provider"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setAssistantName("MyBot")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def yaml = writer.generate(result)

        then:
        yaml.contains("name: MyBot")
        yaml.contains("primary: gpt-4o")
        yaml.contains("fallbacks:")
        yaml.contains("- gpt-4o-mini")
        yaml.contains('api-key: ${OPENAI_API_KEY}')
        !yaml.contains("anthropic")
        !yaml.contains("ollama")
    }

    def "generates YAML for Anthropic provider"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("anthropic")
        result.setLlmModel("claude-sonnet-4-6")
        result.setAssistantName("JClaw")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def yaml = writer.generate(result)

        then:
        yaml.contains("primary: claude-sonnet-4-6")
        yaml.contains('api-key: ${ANTHROPIC_API_KEY}')
        yaml.contains("- claude-haiku-4-5-20251001")
        !yaml.contains("openai")
    }

    def "generates YAML for Ollama provider without fallbacks"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("ollama")
        result.setLlmModel("llama3")
        result.setOllamaBaseUrl("http://localhost:11434")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def yaml = writer.generate(result)

        then:
        yaml.contains("primary: llama3")
        yaml.contains("base-url: http://localhost:11434")
        !yaml.contains("fallbacks:")
        !yaml.contains("api-key")
    }

    def "includes server config in manual mode"() {
        given:
        def result = new OnboardResult()
        result.setFlowMode(OnboardResult.FlowMode.MANUAL)
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setServerPort(9090)
        result.setBindAddress("127.0.0.1")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def yaml = writer.generate(result)

        then:
        yaml.contains("server:")
        yaml.contains("port: 9090")
        yaml.contains("address: 127.0.0.1")
    }

    def "excludes server config in quickstart mode"() {
        given:
        def result = new OnboardResult()
        result.setFlowMode(OnboardResult.FlowMode.QUICKSTART)
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def yaml = writer.generate(result)

        then:
        !yaml.contains("server:")
    }

    def "writes YAML file to config directory"() {
        given:
        def tmpDir = File.createTempDir("jclaw-test-").toPath()
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setAssistantName("JClaw")
        result.setConfigDir(tmpDir)

        when:
        writer.write(result)

        then:
        def yamlFile = tmpDir.resolve("application-local.yml")
        yamlFile.toFile().exists()
        yamlFile.toFile().text.contains("primary: gpt-4o")

        cleanup:
        tmpDir.toFile().deleteDir()
    }
}
