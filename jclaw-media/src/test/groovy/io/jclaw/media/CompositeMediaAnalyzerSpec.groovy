package io.jclaw.media

import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class CompositeMediaAnalyzerSpec extends Specification {

    def "delegates to first matching provider"() {
        given:
        def imageProvider = Mock(MediaAnalysisProvider) {
            name() >> "image-analyzer"
            supports("image/jpeg") >> true
            analyze(_) >> CompletableFuture.completedFuture(
                    MediaAnalysisResult.of("A photo", ["photo"]))
        }
        def analyzer = new CompositeMediaAnalyzer([imageProvider])
        def input = MediaInput.of([1, 2] as byte[], "image/jpeg", "photo.jpg")

        when:
        def result = analyzer.analyze(input).join()

        then:
        result.description() == "A photo"
        result.tags() == ["photo"]
    }

    def "returns empty result when no provider matches"() {
        given:
        def provider = Mock(MediaAnalysisProvider) {
            supports(_) >> false
        }
        def analyzer = new CompositeMediaAnalyzer([provider])
        def input = MediaInput.of([1] as byte[], "application/pdf", "doc.pdf")

        when:
        def result = analyzer.analyze(input).join()

        then:
        result.isEmpty()
    }

    def "handles empty provider list"() {
        given:
        def analyzer = new CompositeMediaAnalyzer([])

        expect:
        analyzer.providerCount() == 0
        analyzer.analyze(MediaInput.of([1] as byte[], "image/png", "a.png")).join().isEmpty()
    }

    def "findProvider returns first match"() {
        given:
        def p1 = Mock(MediaAnalysisProvider) { supports("video/mp4") >> false }
        def p2 = Mock(MediaAnalysisProvider) { supports("video/mp4") >> true; name() >> "video" }
        def analyzer = new CompositeMediaAnalyzer([p1, p2])

        expect:
        analyzer.findProvider("video/mp4").isPresent()
        analyzer.findProvider("video/mp4").get().name() == "video"
        !analyzer.findProvider("audio/wav").isPresent()
    }
}
