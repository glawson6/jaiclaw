package io.jclaw.media

import spock.lang.Specification

class MediaAnalysisResultSpec extends Specification {

    def "empty result"() {
        when:
        def result = MediaAnalysisResult.empty()

        then:
        result.description() == ""
        result.tags() == []
        result.metadata() == [:]
        result.confidence() == 0.0d
        result.isEmpty()
    }

    def "of factory with description and tags"() {
        when:
        def result = MediaAnalysisResult.of("A cat sitting on a sofa", ["cat", "furniture"])

        then:
        result.description() == "A cat sitting on a sofa"
        result.tags() == ["cat", "furniture"]
        result.confidence() == 1.0d
        !result.isEmpty()
    }

    def "confidence clamped to range"() {
        expect:
        new MediaAnalysisResult("x", [], [:], -5.0).confidence() == 0.0d
        new MediaAnalysisResult("x", [], [:], 99.0).confidence() == 1.0d
    }

    def "null defaults"() {
        when:
        def result = new MediaAnalysisResult(null, null, null, 0.5)

        then:
        result.description() == ""
        result.tags() == []
        result.metadata() == [:]
    }
}
