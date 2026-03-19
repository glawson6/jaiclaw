package io.jclaw.shell.commands.setup.config

import io.jclaw.shell.commands.setup.OnboardResult
import spock.lang.Specification

import java.nio.file.Path

class EnvFileWriterSpec extends Specification {

    EnvFileWriter writer = new EnvFileWriter()

    def "generates env file for OpenAI provider"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmApiKey("sk-test-key-123")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def env = writer.generate(result)

        then:
        env.contains("export OPENAI_API_KEY=sk-test-key-123")
        !env.contains("ANTHROPIC")
    }

    def "generates env file for Anthropic provider"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("anthropic")
        result.setLlmApiKey("sk-ant-test-key")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def env = writer.generate(result)

        then:
        env.contains("export ANTHROPIC_API_KEY=sk-ant-test-key")
        !env.contains("OPENAI")
    }

    def "skips API key for Ollama"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("ollama")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def env = writer.generate(result)

        then:
        !env.contains("API_KEY=")
    }

    def "includes Telegram enabled flag and bot token when enabled"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmApiKey("sk-test")
        result.setTelegram(new OnboardResult.TelegramConfig("123456:ABC-DEF", true))
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def env = writer.generate(result)

        then:
        env.contains("export TELEGRAM_ENABLED=true")
        env.contains("export TELEGRAM_BOT_TOKEN=123456:ABC-DEF")
    }

    def "includes Slack enabled flag and tokens when enabled"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmApiKey("sk-test")
        result.setSlack(new OnboardResult.SlackConfig("xoxb-token", "signing-secret", "xapp-token", true))
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def env = writer.generate(result)

        then:
        env.contains("export SLACK_ENABLED=true")
        env.contains("export SLACK_BOT_TOKEN=xoxb-token")
        env.contains("export SLACK_SIGNING_SECRET=signing-secret")
        env.contains("export SLACK_APP_TOKEN=xapp-token")
    }

    def "includes Discord enabled flag and tokens when enabled"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmApiKey("sk-test")
        result.setDiscord(new OnboardResult.DiscordConfig("discord-bot-token", "app-id-123", true))
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def env = writer.generate(result)

        then:
        env.contains("export DISCORD_ENABLED=true")
        env.contains("export DISCORD_BOT_TOKEN=discord-bot-token")
        env.contains("export DISCORD_APPLICATION_ID=app-id-123")
    }

    def "includes security mode in env file"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmApiKey("sk-test")
        result.setSecurityMode("api-key")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def env = writer.generate(result)

        then:
        env.contains("export JCLAW_SECURITY_MODE=api-key")
        !env.contains("JCLAW_API_KEY=")
    }

    def "includes custom API key in env file when set"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmApiKey("sk-test")
        result.setSecurityMode("api-key")
        result.setApiKey("my-custom-key")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def env = writer.generate(result)

        then:
        env.contains("export JCLAW_SECURITY_MODE=api-key")
        env.contains("export JCLAW_API_KEY=my-custom-key")
    }

    def "includes security mode none in env file"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmApiKey("sk-test")
        result.setSecurityMode("none")
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def env = writer.generate(result)

        then:
        env.contains("export JCLAW_SECURITY_MODE=none")
        !env.contains("JCLAW_API_KEY=")
    }

    def "excludes disabled channels"() {
        given:
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmApiKey("sk-test")
        result.setTelegram(new OnboardResult.TelegramConfig("token", false))
        result.setConfigDir(Path.of("/tmp/jclaw-test"))

        when:
        def env = writer.generate(result)

        then:
        !env.contains("TELEGRAM")
    }

    def "writes env file to config directory"() {
        given:
        def tmpDir = File.createTempDir("jclaw-test-").toPath()
        def result = new OnboardResult()
        result.setLlmProvider("openai")
        result.setLlmApiKey("sk-test-key")
        result.setConfigDir(tmpDir)

        when:
        writer.write(result)

        then:
        def envFile = tmpDir.resolve(".env")
        envFile.toFile().exists()
        envFile.toFile().text.contains("OPENAI_API_KEY=sk-test-key")

        cleanup:
        tmpDir.toFile().deleteDir()
    }
}
