package io.jclaw.shell.commands.setup.config

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import io.jclaw.shell.commands.setup.OnboardResult
import spock.lang.Specification

import java.nio.file.Path

class YamlConfigWriterSpec extends Specification {

    YamlConfigWriter writer = new YamlConfigWriter()
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
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jclaw.identity.name == "MyBot"
        parsed.jclaw.agent.agents['default'].model.primary == "gpt-4o"
        parsed.jclaw.agent.agents['default'].model.fallbacks == ["gpt-4o-mini"]
        parsed.spring.ai.openai.'api-key' == '${OPENAI_API_KEY}'
        !parsed.spring.ai.containsKey("anthropic")
        !parsed.spring.ai.containsKey("ollama")
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
        def parsed = parse(yaml)

        then:
        parsed.jclaw.agent.agents['default'].model.primary == "claude-sonnet-4-6"
        parsed.jclaw.agent.agents['default'].model.fallbacks == ["claude-haiku-4-5-20251001"]
        parsed.spring.ai.anthropic.'api-key' == '${ANTHROPIC_API_KEY}'
        !parsed.spring.ai.containsKey("openai")
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
        def parsed = parse(yaml)

        then:
        parsed.jclaw.agent.agents['default'].model.primary == "llama3"
        !parsed.jclaw.agent.agents['default'].model.containsKey("fallbacks")
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
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

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
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

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
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jclaw.channels.telegram.enabled == true
        parsed.jclaw.channels.telegram.'bot-token' == '${TELEGRAM_BOT_TOKEN}'
        parsed.jclaw.channels.slack.enabled == true
        parsed.jclaw.channels.slack.'bot-token' == '${SLACK_BOT_TOKEN}'
        parsed.jclaw.channels.slack.'signing-secret' == '${SLACK_SIGNING_SECRET}'
        parsed.jclaw.channels.slack.'app-token' == '${SLACK_APP_TOKEN}'
        parsed.jclaw.channels.discord.enabled == true
        parsed.jclaw.channels.discord.'bot-token' == '${DISCORD_BOT_TOKEN}'
        parsed.jclaw.channels.discord.'application-id' == '${DISCORD_APPLICATION_ID}'
    }

    def "excludes channels section when none enabled"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        !parsed.jclaw.containsKey("channels")
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
        def parsed = parse(yamlFile.toFile().text)
        parsed.jclaw.agent.agents['default'].model.primary == "gpt-4o"

        cleanup:
        tmpDir.toFile().deleteDir()
    }

    def "includes security section with default api-key mode"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jclaw.security.mode == "api-key"
        !parsed.jclaw.security.containsKey("api-key")
    }

    def "includes security api-key placeholder when custom key set"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setSecurityMode("api-key")
        result.setApiKey("my-custom-key")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jclaw.security.mode == "api-key"
        parsed.jclaw.security.'api-key' == '${JCLAW_API_KEY}'
    }

    def "includes security mode none when disabled"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setSecurityMode("none")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def yaml = writer.generate(result)
        def parsed = parse(yaml)

        then:
        parsed.jclaw.security.mode == "none"
    }

    def "includes MCP server config"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmModel("gpt-4o")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))
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
        parsed.jclaw.'mcp-servers'.'my-server'.type == "stdio"
        parsed.jclaw.'mcp-servers'.'my-server'.command == "npx"
        parsed.jclaw.'mcp-servers'.'my-server'.args == ["-y", "my-server"]
        parsed.jclaw.'mcp-servers'.'remote-server'.type == "http"
        parsed.jclaw.'mcp-servers'.'remote-server'.url == "https://example.com/mcp"
        parsed.jclaw.'mcp-servers'.'remote-server'.'auth-token' == '${MCP_REMOTE_SERVER_TOKEN}'
    }
}
