package io.jclaw.voice

import spock.lang.Specification

class TtsDirectiveParserSpec extends Specification {

    TtsDirectiveParser parser = new TtsDirectiveParser()

    def "plain text without directives returns single segment"() {
        when:
        def segments = parser.parse("Hello world")

        then:
        segments.size() == 1
        segments[0].text() == "Hello world"
        segments[0].params().isEmpty()
    }

    def "parses single directive"() {
        when:
        def segments = parser.parse("Hello [[tts:voice=alloy]]spoken text[[/tts]] end")

        then:
        segments.size() == 3
        segments[0].text() == "Hello"
        segments[1].text() == "spoken text"
        segments[1].voice() == "alloy"
        segments[2].text() == "end"
    }

    def "parses directive with multiple params"() {
        when:
        def segments = parser.parse("[[tts:voice=nova,provider=openai]]text[[/tts]]")

        then:
        segments.size() == 1
        segments[0].voice() == "nova"
        segments[0].provider() == "openai"
    }

    def "stripDirectives removes all directive markers"() {
        when:
        def result = parser.stripDirectives("Hello [[tts:voice=alloy]]world[[/tts]] end")

        then:
        result == "Hello world end"
    }

    def "handles null and empty input"() {
        expect:
        parser.parse(null).isEmpty()
        parser.parse("").isEmpty()
    }
}
