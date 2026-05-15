package io.jaiclaw.config.prompt

import io.jaiclaw.config.SystemPromptConfig
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import spock.lang.Specification

class ClasspathSystemPromptLoaderSpec extends Specification {

    ResourceLoader resourceLoader = Mock()
    ClasspathSystemPromptLoader loader = new ClasspathSystemPromptLoader(resourceLoader)

    def "loads resource from classpath via ResourceLoader"() {
        given:
        def config = new SystemPromptConfig("classpath", null, "prompts/system.txt", false)
        def resource = Mock(Resource) {
            exists() >> true
            getContentAsString(_) >> "You are a helpful assistant."
        }
        resourceLoader.getResource("classpath:prompts/system.txt") >> resource

        when:
        def result = loader.load(config)

        then:
        result == "You are a helpful assistant."
    }

    def "throws when source is null"() {
        given:
        def config = new SystemPromptConfig("classpath", null, null, false)

        when:
        loader.load(config)

        then:
        def e = thrown(SystemPromptLoadException)
        e.message.contains("requires a 'source' path")
    }

    def "throws when source is blank"() {
        given:
        def config = new SystemPromptConfig("classpath", null, "  ", false)

        when:
        loader.load(config)

        then:
        def e = thrown(SystemPromptLoadException)
        e.message.contains("requires a 'source' path")
    }

    def "throws when resource does not exist"() {
        given:
        def config = new SystemPromptConfig("classpath", null, "missing.txt", false)
        def resource = Mock(Resource) {
            exists() >> false
        }
        resourceLoader.getResource("classpath:missing.txt") >> resource

        when:
        loader.load(config)

        then:
        def e = thrown(SystemPromptLoadException)
        e.message.contains("Classpath resource not found: missing.txt")
    }

    def "supports classpath strategy case-insensitively"() {
        expect:
        loader.supports("classpath")
        loader.supports("CLASSPATH")
        loader.supports("Classpath")
        !loader.supports("file")
        !loader.supports("inline")
    }

    def "prefixes source with classpath: when no scheme present"() {
        given:
        def config = new SystemPromptConfig("classpath", null, "prompts/system.txt", false)
        def resource = Mock(Resource) {
            exists() >> true
            getContentAsString(_) >> "content"
        }

        when:
        loader.load(config)

        then:
        1 * resourceLoader.getResource("classpath:prompts/system.txt") >> resource
    }

    def "passes through source with existing scheme prefix"() {
        given:
        def config = new SystemPromptConfig("classpath", null, "classpath*:prompts/*.txt", false)
        def resource = Mock(Resource) {
            exists() >> true
            getContentAsString(_) >> "content"
        }

        when:
        loader.load(config)

        then:
        1 * resourceLoader.getResource("classpath*:prompts/*.txt") >> resource
    }

    def "wraps IOException in SystemPromptLoadException"() {
        given:
        def config = new SystemPromptConfig("classpath", null, "prompts/broken.txt", false)
        def resource = Mock(Resource) {
            exists() >> true
            getContentAsString(_) >> { throw new IOException("read error") }
        }
        resourceLoader.getResource("classpath:prompts/broken.txt") >> resource

        when:
        loader.load(config)

        then:
        def e = thrown(SystemPromptLoadException)
        e.message.contains("Failed to read classpath resource: prompts/broken.txt")
        e.cause instanceof IOException
    }
}
