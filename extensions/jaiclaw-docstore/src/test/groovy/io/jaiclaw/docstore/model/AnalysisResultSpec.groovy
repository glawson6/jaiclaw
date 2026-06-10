package io.jaiclaw.docstore.model

import spock.lang.Specification

class AnalysisResultSpec extends Specification {

    def "compact constructor coerces nulls to empty collections"() {
        when:
        AnalysisResult ar = new AnalysisResult("s", "t", null, null, null,
                AnalysisResult.AnalysisLevel.LLM)

        then:
        ar.topics() == []
        ar.entities() == []
        ar.metadata() == [:]
        ar.level() == AnalysisResult.AnalysisLevel.LLM
    }

    def "Builder accepts every setter"() {
        when:
        AnalysisResult ar = AnalysisResult.builder()
                .summary("sum")
                .extractedText("text")
                .topics(["a", "b"])
                .entities(["x"])
                .metadata(["k1": "v1"])
                .level(AnalysisResult.AnalysisLevel.BASIC)
                .build()

        then:
        ar.summary() == "sum"
        ar.extractedText() == "text"
        ar.topics() == ["a", "b"]
        ar.entities() == ["x"]
        ar.metadata() == ["k1": "v1"]
        ar.level() == AnalysisResult.AnalysisLevel.BASIC
    }

    def "AnalysisLevel enum has BASIC and LLM"() {
        expect:
        AnalysisResult.AnalysisLevel.values() as Set == [
                AnalysisResult.AnalysisLevel.BASIC,
                AnalysisResult.AnalysisLevel.LLM
        ] as Set
    }
}
