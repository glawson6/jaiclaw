package io.jaiclaw.shell.commands.prompt

import io.jaiclaw.config.JaiClawProperties
import io.jaiclaw.shell.commands.setup.config.YamlConfigWriter
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class YamlConfigWriterMergeSpec extends Specification {

    @TempDir
    Path tmp

    YamlConfigWriter writer = new YamlConfigWriter(JaiClawProperties.builder().build())

    def "merge creates the file with the nested key when none exists"() {
        given:
        Path target = tmp.resolve("application-local.yml")

        when:
        writer.merge(target, "jaiclaw.shell.prompt.format", '${identity} > ')

        then:
        def content = Files.readString(target)
        content.contains("jaiclaw:")
        content.contains("shell:")
        content.contains("prompt:")
        content.contains('format: "${identity} > "') || content.contains("format: \${identity} > ")
    }

    def "merge preserves unrelated existing keys"() {
        given:
        Path target = tmp.resolve("application-local.yml")
        Files.writeString(target, """\
jaiclaw:
  identity:
    name: Existing
  shell:
    prompt:
      format: old>
spring:
  ai:
    anthropic:
      api-key: \${ANTHROPIC_API_KEY}
""")

        when:
        writer.merge(target, "jaiclaw.shell.prompt.format", "new> ")

        then:
        def content = Files.readString(target)
        content.contains("name: Existing")
        content.contains("anthropic")
        content.contains("new>")
        !content.contains("old>")
    }

    def "merge rejects blank keys"() {
        given:
        Path target = tmp.resolve("application-local.yml")

        when:
        writer.merge(target, "", "x")

        then:
        thrown(IllegalArgumentException)
    }
}
