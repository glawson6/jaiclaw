package io.jaiclaw.autoconfigure.secrets

import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that {@link SecretsEnvironmentPostProcessor} installs a
 * PropertySource that resolves placeholders through the configured
 * provider chain, and that it stays out of the way when no provider
 * is configured.
 */
class SecretsEnvironmentPostProcessorSpec extends Specification {

    @TempDir
    Path tempDir

    def "no-op when jaiclaw.secrets.provider is unset"() {
        given:
        ConfigurableEnvironment env = new StandardEnvironment()
        SpringApplication app = new SpringApplication()

        when:
        new SecretsEnvironmentPostProcessor().postProcessEnvironment(env, app)

        then:
        !env.propertySources.contains("jaiclawSecrets")
    }

    def "installs PropertySource that resolves through file provider"() {
        given:
        Path envFile = tempDir.resolve(".env")
        Files.writeString(envFile, "ANTHROPIC_API_KEY=sk-from-file\n")
        ConfigurableEnvironment env = new StandardEnvironment()
        env.propertySources.addLast(new MapPropertySource("test-config", [
                (SecretsConfig.PROP_PROVIDER):   "file",
                (SecretsConfig.PROP_FILE_PATH):  envFile.toString(),
        ]))
        SpringApplication app = new SpringApplication()

        when:
        new SecretsEnvironmentPostProcessor().postProcessEnvironment(env, app)

        then:
        env.propertySources.contains("jaiclawSecrets")
        env.getProperty("ANTHROPIC_API_KEY") == "sk-from-file"
    }

    def "secrets PropertySource sits high enough to win over later sources"() {
        given:
        Path envFile = tempDir.resolve(".env")
        Files.writeString(envFile, "ANTHROPIC_API_KEY=sk-from-file\n")
        ConfigurableEnvironment env = new StandardEnvironment()
        // Lower-precedence source mimics application.yml defaults
        env.propertySources.addLast(new MapPropertySource("application-defaults", [
                ANTHROPIC_API_KEY: "sk-default-fallback",
        ]))
        env.propertySources.addLast(new MapPropertySource("test-config", [
                (SecretsConfig.PROP_PROVIDER):  "file",
                (SecretsConfig.PROP_FILE_PATH): envFile.toString(),
        ]))
        SpringApplication app = new SpringApplication()

        when:
        new SecretsEnvironmentPostProcessor().postProcessEnvironment(env, app)

        then: "secrets source wins over the application defaults"
        env.getProperty("ANTHROPIC_API_KEY") == "sk-from-file"
    }

    def "missing keys fall through to lower-precedence sources"() {
        given:
        Path envFile = tempDir.resolve(".env")
        Files.writeString(envFile, "OTHER_KEY=in-file\n") // does NOT contain ANTHROPIC_API_KEY
        ConfigurableEnvironment env = new StandardEnvironment()
        env.propertySources.addLast(new MapPropertySource("application-defaults", [
                ANTHROPIC_API_KEY: "sk-default",
        ]))
        env.propertySources.addLast(new MapPropertySource("test-config", [
                (SecretsConfig.PROP_PROVIDER):  "file",
                (SecretsConfig.PROP_FILE_PATH): envFile.toString(),
        ]))
        SpringApplication app = new SpringApplication()

        when:
        new SecretsEnvironmentPostProcessor().postProcessEnvironment(env, app)

        then: "fall-through resolution finds the default"
        env.getProperty("ANTHROPIC_API_KEY") == "sk-default"
        env.getProperty("OTHER_KEY") == "in-file"
    }
}
