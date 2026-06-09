package io.jaiclaw.shell.commands.setup.config

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import io.jaiclaw.config.JaiClawProperties
import io.jaiclaw.config.ModelsProperties
import io.jaiclaw.config.ModelsProperties.ModelProviderConfig
import io.jaiclaw.shell.commands.setup.OnboardResult
import spock.lang.Specification

import java.nio.file.Path

class YamlConfigWriterSpec extends Specification {

    static JaiClawProperties testProperties() {
        JaiClawProperties.builder()
                .models(new ModelsProperties(Map.of(
                        "openai", ModelProviderConfig.builder()
                                .displayName("OpenAI")
                                .fallbackModel("gpt-4o-mini")
                                .wizardModels(["gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "o3-mini"])
                                .build(),
                        "anthropic", ModelProviderConfig.builder()
                                .displayName("Anthropic")
                                .fallbackModel("claude-haiku-4-5-20251001")
                                .wizardModels(["claude-sonnet-4-6", "claude-opus-4-6", "claude-haiku-4-5-20251001"])
                                .build(),
                        "bedrock", ModelProviderConfig.builder()
                                .displayName("AWS Bedrock")
                                .fallbackModel("us.anthropic.claude-3-haiku-20240307-v1:0")
                                .wizardModels(["us.anthropic.claude-3-5-sonnet-20241022-v2:0", "us.anthropic.claude-3-haiku-20240307-v1:0"])
                                .build(),
                        "ollama", ModelProviderConfig.builder()
                                .displayName("Ollama")
                                .wizardModels(["llama3", "mistral"])
                                .build()
                )))
                .build()
    }

    YamlConfigWriter writer = new YamlConfigWriter(testProperties())
    YAMLMapper mapper = new YAMLMapper()

    private Map<String, Object> parse(String yaml) {
        mapper.readValue(yaml, Map)
    }

    def "generates YAML for OpenAI provider"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setAssistantName("MyBot")
        result.setConfigDir(Path.of("/tmp/jaiclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jaiclaw.identity.name == "MyBot"
        parsed.jaiclaw.agent.agents['default'].model.primary == "gpt-4o"
        parsed.jaiclaw.agent.agents['default'].model.fallbacks == ["gpt-4o-mini"]
        parsed.spring.ai.openai.'api-key' == '${OPENAI_API_KEY}'
        !parsed.spring.ai.containsKey("anthropic")
        !parsed.spring.ai.containsKey("ollama")
    }

    def "generates YAML for Anthropic provider"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("anthropic")
        result.setLlmModel("claude-sonnet-4-6")
        result.setAssistantName("JaiClaw")
        result.setConfigDir(Path.of("/tmp/jaiclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jaiclaw.agent.agents['default'].model.primary == "claude-sonnet-4-6"
        parsed.jaiclaw.agent.agents['default'].model.fallbacks == ["claude-haiku-4-5-20251001"]
        parsed.spring.ai.anthropic.'api-key' == '${ANTHROPIC_API_KEY}'
        !parsed.spring.ai.containsKey("openai")
    }

    def "generates YAML for Ollama provider without fallbacks"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("ollama")
        result.setLlmModel("llama3")
        result.setOllamaBaseUrl("http://localhost:11434")
        result.setConfigDir(Path.of("/tmp/jaiclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jaiclaw.agent.agents['default'].model.primary == "llama3"
        !parsed.jaiclaw.agent.agents['default'].model.containsKey("fallbacks")
        parsed.spring.ai.ollama.'base-url' == "http://localhost:11434"
        !parsed.spring.ai.containsKey("openai")
        !parsed.spring.ai.containsKey("anthropic")
    }

    def "includes server config in manual mode"() {
        given:
        def result = new OnboardResult()
        result.setFlowMode(OnboardResult.FlowMode.MANUAL)
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setServerPort(9090)
        result.setBindAddress("127.0.0.1")
        result.setConfigDir(Path.of("/tmp/jaiclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.server.port == 9090
        parsed.server.address == "127.0.0.1"
    }

    def "excludes server config in quickstart mode"() {
        given:
        def result = new OnboardResult()
        result.setFlowMode(OnboardResult.FlowMode.QUICKSTART)
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setConfigDir(Path.of("/tmp/jaiclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        !parsed.containsKey("server")
    }

    def "includes enabled channels"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setTelegram(new OnboardResult.TelegramConfig("token", true))
        result.setSlack(new OnboardResult.SlackConfig("xoxb", "secret", "xapp", true))
        result.setDiscord(new OnboardResult.DiscordConfig("disc-token", "app-id", true))
        result.setConfigDir(Path.of("/tmp/jaiclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jaiclaw.channels.telegram.enabled == true
        parsed.jaiclaw.channels.telegram.'bot-token' == '${TELEGRAM_BOT_TOKEN}'
        parsed.jaiclaw.channels.slack.enabled == true
        parsed.jaiclaw.channels.slack.'bot-token' == '${SLACK_BOT_TOKEN}'
        parsed.jaiclaw.channels.slack.'signing-secret' == '${SLACK_SIGNING_SECRET}'
        parsed.jaiclaw.channels.slack.'app-token' == '${SLACK_APP_TOKEN}'
        parsed.jaiclaw.channels.discord.enabled == true
        parsed.jaiclaw.channels.discord.'bot-token' == '${DISCORD_BOT_TOKEN}'
        parsed.jaiclaw.channels.discord.'application-id' == '${DISCORD_APPLICATION_ID}'
    }

    def "excludes channels section when none enabled"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setConfigDir(Path.of("/tmp/jaiclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        !parsed.jaiclaw.containsKey("channels")
    }

    def "writes YAML file to config directory"() {
        given:
        def tmpDir = File.createTempDir("jaiclaw-test-").toPath()
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setAssistantName("JaiClaw")
        result.setConfigDir(tmpDir)

        when:
        writer.write(result)

        then:
        def yamlFile = tmpDir.resolve("application-local.yml")
        yamlFile.toFile().exists()
        def parsed = parse(yamlFile.toFile().text)
        parsed.jaiclaw.agent.agents['default'].model.primary == "gpt-4o"

        cleanup:
        tmpDir.toFile().deleteDir()
    }

    def "includes security section with default api-key mode"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setConfigDir(Path.of("/tmp/jaiclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jaiclaw.security.mode == "api-key"
        !parsed.jaiclaw.security.containsKey("api-key")
    }

    def "includes security api-key placeholder when custom key set"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setSecurityMode("api-key")
        result.setApiKey("my-custom-key")
        result.setConfigDir(Path.of("/tmp/jaiclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jaiclaw.security.mode == "api-key"
        parsed.jaiclaw.security.'api-key' == '${JAICLAW_API_KEY}'
    }

    def "includes security mode none when disabled"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setSecurityMode("none")
        result.setConfigDir(Path.of("/tmp/jaiclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jaiclaw.security.mode == "none"
    }

    def "includes MCP server config"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setConfigDir(Path.of("/tmp/jaiclaw-test"))
        result.setMcpServers([
            new OnboardResult.McpServerConfig(
                "my-server", "A test server", OnboardResult.McpTransportType.STDIO,
                "npx", ["-y", "my-server"], null, null
            ),
            new OnboardResult.McpServerConfig(
                "remote-server", "Remote", OnboardResult.McpTransportType.HTTP,
                null, null, "https://example.com/mcp", "secret-token"
            )
        ])

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jaiclaw.'mcp-servers'.'my-server'.type == "stdio"
        parsed.jaiclaw.'mcp-servers'.'my-server'.command == "npx"
        parsed.jaiclaw.'mcp-servers'.'my-server'.args == ["-y", "my-server"]
        parsed.jaiclaw.'mcp-servers'.'remote-server'.type == "http"
        parsed.jaiclaw.'mcp-servers'.'remote-server'.url == "https://example.com/mcp"
        parsed.jaiclaw.'mcp-servers'.'remote-server'.'auth-token' == '${MCP_REMOTE_SERVER_TOKEN}'
    }

    def "uses fallback from config for bedrock provider"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("bedrock")
        result.setLlmModel("us.anthropic.claude-3-5-sonnet-20241022-v2:0")
        result.setAwsRegion("us-east-1")
        result.setConfigDir(Path.of("/tmp/jaiclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jaiclaw.agent.agents['default'].model.fallbacks == ["us.anthropic.claude-3-haiku-20240307-v1:0"]
    }
}
