package io.jaiclaw.pipeline.validation

import spock.lang.Specification

class LevenshteinSpec extends Specification {

    def "distance handles identical strings"() {
        expect:
        Levenshtein.distance("research", "research") == 0
    }

    def "distance handles single substitution"() {
        expect:
        Levenshtein.distance("resarch", "research") == 1
    }

    def "distance handles transposition as two edits"() {
        expect:
        Levenshtein.distance("research", "resraech") == 2
    }

    def "distance handles empty strings"() {
        expect:
        Levenshtein.distance("", "abc") == 3
        Levenshtein.distance("abc", "") == 3
        Levenshtein.distance("", "") == 0
    }

    def "distance handles nulls as empty"() {
        expect:
        Levenshtein.distance(null, "abc") == 3
        Levenshtein.distance("abc", null) == 3
        Levenshtein.distance(null, null) == 0
    }

    def "suggest returns closest within max distance"() {
        expect:
        Levenshtein.suggest("resarch", ["research", "write", "summary"], 2).get() == "research"
    }

    def "suggest returns empty when nothing close enough"() {
        expect:
        !Levenshtein.suggest("xyz", ["research", "write"], 2).isPresent()
    }

    def "suggest returns empty for empty candidate list"() {
        expect:
        !Levenshtein.suggest("anything", [], 2).isPresent()
    }

    def "suggest skips exact match (target itself)"() {
        expect:
        Levenshtein.suggest("research", ["research", "resarch"], 2).get() == "resarch"
    }
}
