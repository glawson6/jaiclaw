package io.jaiclaw.shell.commands.prompt

import io.jaiclaw.config.JaiClawProperties
import io.jaiclaw.shell.commands.setup.config.YamlConfigWriter
import org.springframework.mock.env.MockEnvironment
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class PromptCommandsSpec extends Specification {

    @TempDir
    Path tmp

    MockEnvironment env = new MockEnvironment()
    PromptProperties props = PromptProperties.defaults()
    YamlConfigWriter writer = new YamlConfigWriter(JaiClawProperties.builder().build())

    def "set persists the format string into application-local.yml under jaiclaw.shell.prompt.format"() {
        given:
        Path profileDir = Files.createDirectories(tmp.resolve("profiles/work"))
        PromptCommands cmds = new PromptCommands(props, writer, env, profileDir.toString())

        when:
        String out = cmds.set('${identity}@${profile}> ')

        then:
        out.contains("Saved")
        def yamlFile = profileDir.resolve("application-local.yml")
        Files.exists(yamlFile)
        Files.readString(yamlFile).contains("format:")
        Files.readString(yamlFile).contains('${identity}@${profile}> ')
    }

    def "set updates the live Environment so the running provider sees the new format"() {
        given:
        Path profileDir = Files.createDirectories(tmp.resolve("profiles/work"))
        PromptCommands cmds = new PromptCommands(props, writer, env, profileDir.toString())

        when:
        cmds.set('cli> ')

        then:
        env.getProperty("jaiclaw.shell.prompt.format") == "cli> "
    }

    def "set rejects blank format"() {
        given:
        PromptCommands cmds = new PromptCommands(props, writer, env, tmp.toString())

        expect:
        cmds.set("").contains("must not be blank")
        cmds.set("   ").contains("must not be blank")
    }

    def "prompt shows current format and rendered preview"() {
        given:
        env.setProperty("jaiclaw.identity.name", "Acme")
        env.setProperty("jaiclaw.shell.prompt.format", '${identity}-> ')
        PromptCommands cmds = new PromptCommands(props, writer, env, tmp.toString())

        when:
        String out = cmds.prompt()

        then:
        out.contains('Format:  ${identity}-> ')
        out.contains("Preview: Acme-> ")
    }
}
