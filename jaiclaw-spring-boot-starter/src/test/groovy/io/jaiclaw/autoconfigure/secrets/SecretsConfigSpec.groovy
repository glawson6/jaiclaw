package io.jaiclaw.autoconfigure.secrets

import io.jaiclaw.core.secrets.EnvironmentSecretsProvider
import io.jaiclaw.core.secrets.FileSecretsProvider
import io.jaiclaw.core.secrets.SecretsResolver
import org.springframework.core.env.Environment
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class SecretsConfigSpec extends Specification {

    @TempDir
    Path tempDir

    Environment env = Mock(Environment)

    def "returns null when no provider is set"() {
        given:
        env.getProperty(SecretsConfig.PROP_PROVIDER, "") >> ""

        expect:
        SecretsConfig.build(env) == null
    }

    def "builds env-only chain when provider=env"() {
        given:
        env.getProperty(SecretsConfig.PROP_PROVIDER, "") >> "env"
        env.getProperty(SecretsConfig.PROP_CHAIN_ON_ERROR, "continue") >> "continue"

        when:
        SecretsResolver resolver = SecretsConfig.build(env)

        then:
        resolver != null
        resolver.chain().size() == 1
        resolver.chain()[0] instanceof EnvironmentSecretsProvider
        resolver.onError() == SecretsResolver.OnError.CONTINUE
    }

    def "builds file provider with configured path"() {
        given:
        Path envFile = tempDir.resolve(".env")
        Files.writeString(envFile, "FOO=bar\n")
        env.getProperty(SecretsConfig.PROP_PROVIDER, "") >> "file"
        env.getProperty(SecretsConfig.PROP_FILE_PATH) >> envFile.toString()
        env.getProperty(SecretsConfig.PROP_CHAIN_ON_ERROR, "continue") >> "continue"

        when:
        SecretsResolver resolver = SecretsConfig.build(env)

        then:
        resolver != null
        resolver.chain().size() == 1
        resolver.chain()[0] instanceof FileSecretsProvider
        resolver.getValue("FOO").get() == "bar"
    }

    def "file provider is omitted when path is blank, leaving chain empty"() {
        given:
        env.getProperty(SecretsConfig.PROP_PROVIDER, "") >> "file"
        env.getProperty(SecretsConfig.PROP_FILE_PATH) >> ""
        env.getProperty(SecretsConfig.PROP_CHAIN_ON_ERROR, "continue") >> "continue"

        expect:
        SecretsConfig.build(env) == null
    }

    def "builds composite chain in declared order"() {
        given:
        Path envFile = tempDir.resolve(".env")
        Files.writeString(envFile, "FOO=from-file\n")
        env.getProperty(SecretsConfig.PROP_PROVIDER, "") >> "composite"
        env.getProperty(SecretsConfig.PROP_CHAIN, "env,file") >> "env,file"
        env.getProperty(SecretsConfig.PROP_FILE_PATH) >> envFile.toString()
        env.getProperty(SecretsConfig.PROP_CHAIN_ON_ERROR, "continue") >> "continue"

        when:
        SecretsResolver resolver = SecretsConfig.build(env)

        then:
        resolver != null
        resolver.chain().size() == 2
        resolver.chain()[0].name() == "env"
        resolver.chain()[1].name() == "file"
    }

    def "respects chain-on-error=fail"() {
        given:
        env.getProperty(SecretsConfig.PROP_PROVIDER, "") >> "env"
        env.getProperty(SecretsConfig.PROP_CHAIN_ON_ERROR, "continue") >> "fail"

        when:
        SecretsResolver resolver = SecretsConfig.build(env)

        then:
        resolver.onError() == SecretsResolver.OnError.FAIL
    }

    def "1password provider is skipped silently if extension class is absent"() {
        // The starter doesn't depend on the 1password extension, so its
        // class is not on the test classpath. The factory should return
        // null (with a WARN log) rather than throw.
        given:
        env.getProperty(SecretsConfig.PROP_PROVIDER, "") >> "onepassword"
        env.getProperty(SecretsConfig.PROP_OP_VAULT) >> "MyVault"
        env.getProperty(SecretsConfig.PROP_CHAIN_ON_ERROR, "continue") >> "continue"
        env.getProperty(SecretsConfig.PROP_OP_BINARY, "op") >> "op"
        env.getProperty(SecretsConfig.PROP_OP_TOKEN) >> null
        env.getProperty(SecretsConfig.PROP_OP_TIMEOUT, "10s") >> "10s"

        expect:
        SecretsConfig.build(env) == null  // chain empty after skipping op, build returns null
    }

    def "parseDuration handles common forms"() {
        expect:
        SecretsConfig.parseDuration(input) == expected

        where:
        input    | expected
        "10s"    | Duration.ofSeconds(10)
        "500ms"  | Duration.ofMillis(500)
        "1m"     | Duration.ofMinutes(1)
        "30"     | Duration.ofSeconds(30)
    }
}
