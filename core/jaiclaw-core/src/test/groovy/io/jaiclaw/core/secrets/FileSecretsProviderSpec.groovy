package io.jaiclaw.core.secrets

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class FileSecretsProviderSpec extends Specification {

    @TempDir
    Path tempDir

    private Path writeEnv(String contents) {
        Path file = tempDir.resolve(".env")
        Files.writeString(file, contents)
        file
    }

    def "reads simple KEY=value pairs"() {
        given:
        Path file = writeEnv("""\
            # comment
            ANTHROPIC_API_KEY=sk-abc
            OPENAI_API_KEY=sk-def
            """.stripIndent())
        FileSecretsProvider provider = new FileSecretsProvider(file)

        expect:
        provider.get("ANTHROPIC_API_KEY").get() == "sk-abc"
        provider.get("OPENAI_API_KEY").get() == "sk-def"
        provider.get("MISSING").isEmpty()
    }

    def "strips quotes around values"() {
        given:
        Path file = writeEnv('''\
            DOUBLE="value with spaces"
            SINGLE='another value'
            BARE=plain
            '''.stripIndent())
        FileSecretsProvider provider = new FileSecretsProvider(file)

        expect:
        provider.get("DOUBLE").get() == "value with spaces"
        provider.get("SINGLE").get() == "another value"
        provider.get("BARE").get() == "plain"
    }

    def "accepts 'export' prefix for shell compat"() {
        given:
        Path file = writeEnv("export ANTHROPIC_API_KEY=sk-shell\n")
        FileSecretsProvider provider = new FileSecretsProvider(file)

        expect:
        provider.get("ANTHROPIC_API_KEY").get() == "sk-shell"
    }

    def "ignores blank lines and comments"() {
        given:
        Path file = writeEnv("""\

            # leading comment
            ANTHROPIC_API_KEY=sk-abc

            # trailing comment

            """.stripIndent())
        FileSecretsProvider provider = new FileSecretsProvider(file)

        expect:
        provider.get("ANTHROPIC_API_KEY").get() == "sk-abc"
    }

    def "skips malformed lines silently"() {
        given:
        Path file = writeEnv("""\
            not a valid line
            =no-key
            ANTHROPIC_API_KEY=sk-abc
            """.stripIndent())
        FileSecretsProvider provider = new FileSecretsProvider(file)

        expect:
        provider.get("ANTHROPIC_API_KEY").get() == "sk-abc"
    }

    def "treats missing file as empty"() {
        given:
        Path missing = tempDir.resolve("does-not-exist.env")
        FileSecretsProvider provider = new FileSecretsProvider(missing)

        expect:
        provider.get("any").isEmpty()
        provider.getAll("").isEmpty()
    }

    def "getAll strips prefix"() {
        given:
        Path file = writeEnv("""\
            tenant-acme.anthropic=sk-acme
            tenant-acme.openai=sk-acme-oai
            tenant-other.anthropic=sk-other
            """.stripIndent())
        FileSecretsProvider provider = new FileSecretsProvider(file)

        when:
        Map<String, String> acme = provider.getAll("tenant-acme.")

        then:
        acme.size() == 2
        acme.anthropic == "sk-acme"
        acme.openai == "sk-acme-oai"
    }

    def "refresh forces re-read"() {
        given:
        Path file = writeEnv("K=v1\n")
        FileSecretsProvider provider = new FileSecretsProvider(file)
        provider.get("K") // prime the cache

        when:
        Files.writeString(file, "K=v2\n")

        then: "still cached"
        provider.get("K").get() == "v1"

        when:
        provider.refresh()

        then: "reads new value"
        provider.get("K").get() == "v2"
    }

    def "name() returns 'file'"() {
        expect:
        new FileSecretsProvider(tempDir.resolve("any")).name() == "file"
    }
}
