package io.jclaw.compaction

import io.jclaw.core.model.UserMessage
import io.jclaw.core.model.AssistantMessage
import spock.lang.Specification

class TokenEstimatorSpec extends Specification {

    TokenEstimator estimator = new TokenEstimator()

    def "estimates tokens from text length"() {
        expect:
        estimator.estimateTokens("hello world") > 0
    }

    def "empty text returns 0 tokens"() {
        expect:
        estimator.estimateTokens("") == 0
        estimator.estimateTokens((String) null) == 0
    }

    def "estimates tokens for a message including overhead"() {
        given:
        def message = new UserMessage("1", "hello world", "user1")

        expect:
        estimator.estimateTokens(message) > estimator.estimateTokens("hello world")
    }

    def "estimates tokens for a list of messages"() {
        given:
        def messages = [
            new UserMessage("1", "hello", "user1"),
            new AssistantMessage("2", "world", "model1")
        ]

        expect:
        estimator.estimateTokens(messages) > 0
        estimator.estimateTokens(messages) == estimator.estimateTokens(messages[0]) + estimator.estimateTokens(messages[1])
    }

    def "longer text produces more tokens"() {
        expect:
        estimator.estimateTokens("a" * 100) < estimator.estimateTokens("a" * 1000)
    }
}
