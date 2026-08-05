package io.jaiclaw.cli.github

import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ResourceLoader
import spock.lang.Specification

class SystemPromptLoaderSpec extends Specification {

    def "returns file contents when resource exists"() {
        given:
        def loader = Mock(ResourceLoader)
        loader.getResource("classpath:prompts/test.md") >> new ByteArrayResource("hello prompt".bytes)
        def props = new CliGithubProperties("classpath:prompts/test.md", 20)
        def sysLoader = new SystemPromptLoader(props, loader)

        expect:
        sysLoader.load() == "hello prompt"
    }

    def "caches result across invocations"() {
        given:
        def loader = Mock(ResourceLoader)
        def props = new CliGithubProperties("classpath:prompts/x.md", 20)
        def sysLoader = new SystemPromptLoader(props, loader)

        when:
        String first = sysLoader.load()
        String second = sysLoader.load()

        then:
        1 * loader.getResource("classpath:prompts/x.md") >> new ByteArrayResource("cached".bytes)
        first == "cached"
        second == "cached"
    }

    def "returns empty string when file does not exist (fail-quiet)"() {
        given:
        def loader = Mock(ResourceLoader)
        def missing = new ByteArrayResource(new byte[0]) {
            boolean exists() { false }
        }
        loader.getResource(_ as String) >> missing
        def props = new CliGithubProperties("classpath:missing.md", 20)
        def sysLoader = new SystemPromptLoader(props, loader)

        expect:
        sysLoader.load() == ""
    }
}
