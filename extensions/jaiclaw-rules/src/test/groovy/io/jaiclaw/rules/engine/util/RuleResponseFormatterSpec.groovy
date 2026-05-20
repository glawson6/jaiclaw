package io.jaiclaw.rules.engine.util

import io.jaiclaw.rules.engine.model.RuleExecutionResponse
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

/**
 * Spock specification for RuleResponseFormatter.
 * Tests the formatting of rule execution responses for LLM consumption.
 */
class RuleResponseFormatterSpec extends Specification {

    def "formatAsSimpleString should handle null response"() {
        when:
        def result = RuleResponseFormatter.formatAsSimpleString(null)

        then:
        result == "No response available"
    }

    def "formatAsSimpleString should format successful response with messages"() {
        given:
        def response = RuleExecutionResponse.builder()
            .success(true)
            .ruleName("TestRule")
            .rulesFired(3)
            .messages(["Message 1", "Message 2"])
            .results(["key1": "value1", "key2": "value2"])
            .executionTime(Instant.now())
            .build()

        when:
        def result = RuleResponseFormatter.formatAsSimpleString(response)

        then:
        result.contains("Rule Execution: SUCCESS")
        result.contains("Rule: TestRule")
        result.contains("Rules Fired: 3")
        result.contains("Message 1")
        result.contains("Message 2")
        result.contains("key1: value1")
        result.contains("key2: value2")
    }

    def "formatAsSimpleString should format failed response"() {
        given:
        def response = RuleExecutionResponse.builder()
            .success(false)
            .ruleName("FailedRule")
            .error("Rule execution error occurred")
            .executionTime(Instant.now())
            .build()

        when:
        def result = RuleResponseFormatter.formatAsSimpleString(response)

        then:
        result.contains("Rule Execution: FAILED")
        result.contains("Rule: FailedRule")
        result.contains("Error: Rule execution error occurred")
    }

    def "formatAsSimpleString should handle response with empty messages"() {
        given:
        def response = RuleExecutionResponse.builder()
            .success(true)
            .ruleName("EmptyMessagesRule")
            .rulesFired(1)
            .messages([])
            .build()

        when:
        def result = RuleResponseFormatter.formatAsSimpleString(response)

        then:
        result.contains("Rule Execution: SUCCESS")
        result.contains("Rule: EmptyMessagesRule")
        !result.contains("Messages:")
    }

    def "formatAsSummary should handle null response"() {
        when:
        def result = RuleResponseFormatter.formatAsSummary(null)

        then:
        result == "No response"
    }

    def "formatAsSummary should format successful response concisely"() {
        given:
        def response = RuleExecutionResponse.builder()
            .success(true)
            .ruleName("SummaryRule")
            .rulesFired(2)
            .messages(["Decision made", "Action taken"])
            .build()

        when:
        def result = RuleResponseFormatter.formatAsSummary(response)

        then:
        result.contains("Rule 'SummaryRule' executed successfully")
        result.contains("2 rules fired")
        result.contains("Decision made; Action taken")
    }

    def "formatAsSummary should format failed response concisely"() {
        given:
        def response = RuleExecutionResponse.builder()
            .success(false)
            .ruleName("FailedSummaryRule")
            .error("Validation error")
            .build()

        when:
        def result = RuleResponseFormatter.formatAsSummary(response)

        then:
        result.contains("Rule 'FailedSummaryRule' failed")
        result.contains("Validation error")
    }

    def "formatAsSummary should handle missing error message"() {
        given:
        def response = RuleExecutionResponse.builder()
            .success(false)
            .ruleName("NoErrorMessageRule")
            .build()

        when:
        def result = RuleResponseFormatter.formatAsSummary(response)

        then:
        result.contains("Rule 'NoErrorMessageRule' failed")
        result.contains("Unknown error")
    }
}
