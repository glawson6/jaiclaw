package io.jaiclaw.shell.commands.setup.config

import io.jaiclaw.shell.commands.setup.OnboardResult
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class OnePasswordTemplateWriterSpec extends Specification {

    @TempDir
    Path tempDir

    OnePasswordTemplateWriter writer = new OnePasswordTemplateWriter()

    def "no-op when result has no OnePasswordConfig"() {
        given:
        def result = new OnboardResult()  // onePassword is null

        when:
        Path tpl = writer.writeIfConfigured(result, tempDir)

        then:
        tpl == null
        !Files.exists(tempDir.resolve(".env.op.tpl"))
    }

    def "writes a template with one reference per configured key"() {
        given:
        def result = new OnboardResult()
        result.setOnePassword(new OnboardResult.OnePasswordConfig(
                "TapTech-Security",
                ["ANTHROPIC_API_KEY", "OPENAI_API_KEY", "TELEGRAM_BOT_TOKEN"]))

        when:
        Path tpl = writer.writeIfConfigured(result, tempDir)

        then:
        tpl == tempDir.resolve(".env.op.tpl")
        String body = Files.readString(tpl)
        body.contains("ANTHROPIC_API_KEY=op://TapTech-Security/anthropic/api-key")
        body.contains("OPENAI_API_KEY=op://TapTech-Security/openai/api-key")
        body.contains("TELEGRAM_BOT_TOKEN=op://TapTech-Security/telegram/bot-token")
    }

    def "header documents the template format and safety guarantee"() {
        given:
        def result = new OnboardResult()
        result.setOnePassword(new OnboardResult.OnePasswordConfig(
                "v", ["FOO_BAR"]))

        when:
        Path tpl = writer.writeIfConfigured(result, tempDir)

        then:
        String body = Files.readString(tpl)
        body.startsWith("#")
        body.contains("REFERENCES only")
        body.contains("jaiclaw --use-1password")
    }

    def "single-token env var uses 'value' as the field"() {
        // SLACK_APP_TOKEN → item 'slack', field 'app-token'
        // but a token-only var like 'JAICLAW_API_KEY' is fine too:
        // item 'jaiclaw', field 'api-key'.
        // A NO_UNDERSCORE var should still resolve.
        expect:
        OnePasswordTemplateWriter.buildRef("vault", input) == expected

        where:
        input              | expected
        "JAICLAW_API_KEY"  | "op://vault/jaiclaw/api-key"
        "SLACK_APP_TOKEN"  | "op://vault/slack/app-token"
        "NOUNDERSCORE"     | "op://vault/nounderscore/value"
        "X_Y_Z"            | "op://vault/x/y-z"
    }

    def "file permissions are restricted to owner read/write on POSIX"() {
        given:
        def result = new OnboardResult()
        result.setOnePassword(new OnboardResult.OnePasswordConfig("v", ["K"]))

        when:
        Path tpl = writer.writeIfConfigured(result, tempDir)

        then:
        // 0600 on POSIX; skipped on Windows-style filesystems.
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return
        }
        Set perms = Files.getPosixFilePermissions(tpl)
        perms.size() == 2
        perms*.toString().sort() == ["OWNER_READ", "OWNER_WRITE"].sort()
    }
}
