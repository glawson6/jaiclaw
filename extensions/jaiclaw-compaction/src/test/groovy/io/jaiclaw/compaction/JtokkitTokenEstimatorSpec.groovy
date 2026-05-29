package io.jaiclaw.compaction

import io.jaiclaw.core.model.UserMessage
import spock.lang.Specification

class JtokkitTokenEstimatorSpec extends Specification {

    def estimator = new JtokkitTokenEstimator()

    def "null or empty text returns 0"() {
        expect:
        estimator.estimateTokens((String) null) == 0
        estimator.estimateTokens("") == 0
    }

    def "single word returns positive count"() {
        expect:
        estimator.estimateTokens("hello") > 0
    }

    def "longer text produces more tokens than shorter text"() {
        given:
        def shortText = "Hi"
        def longText = "The quick brown fox jumps over the lazy dog multiple times in the park"

        expect:
        estimator.estimateTokens(longText) > estimator.estimateTokens(shortText)
    }

    def "message estimation includes overhead"() {
        given:
        def text = "Hello, world!"
        def message = new UserMessage(UUID.randomUUID().toString(), text, "user1")

        expect:
        estimator.estimateTokens(message) > estimator.estimateTokens(text)
    }

    def "list estimation sums individual messages"() {
        given:
        def m1 = new UserMessage(UUID.randomUUID().toString(), "Hello", "user1")
        def m2 = new UserMessage(UUID.randomUUID().toString(), "World", "user1")

        expect:
        estimator.estimateTokens([m1, m2]) == estimator.estimateTokens(m1) + estimator.estimateTokens(m2)
    }
}
