package io.jclaw.gateway.observability

import spock.lang.Specification

class GatewayMetricsSpec extends Specification {

    def "records messages by channel"() {
        given:
        def metrics = new GatewayMetrics()

        when:
        metrics.recordMessage("telegram")
        metrics.recordMessage("telegram")
        metrics.recordMessage("slack")

        then:
        metrics.totalMessages() == 3
        metrics.messagesForChannel("telegram") == 2
        metrics.messagesForChannel("slack") == 1
        metrics.messagesForChannel("discord") == 0
    }

    def "records errors by channel"() {
        given:
        def metrics = new GatewayMetrics()

        when:
        metrics.recordError("telegram")
        metrics.recordError("telegram")

        then:
        metrics.totalErrors() == 2
        metrics.errorsForChannel("telegram") == 2
        metrics.errorsForChannel("slack") == 0
    }

    def "records tool executions"() {
        given:
        def metrics = new GatewayMetrics()

        when:
        metrics.recordToolExecution(true)
        metrics.recordToolExecution(true)
        metrics.recordToolExecution(false)

        then:
        metrics.totalToolExecutions() == 3
        metrics.totalToolErrors() == 1
    }

    def "snapshot returns all metrics"() {
        given:
        def metrics = new GatewayMetrics()
        metrics.recordMessage("telegram")
        metrics.recordError("slack")
        metrics.recordToolExecution(true)

        when:
        def snap = metrics.snapshot()

        then:
        snap.totalMessages == 1L
        snap.totalErrors == 1L
        snap.totalToolExecutions == 1L
        snap["messages.telegram"] == 1L
    }

    def "reset clears all counters"() {
        given:
        def metrics = new GatewayMetrics()
        metrics.recordMessage("telegram")
        metrics.recordError("slack")

        when:
        metrics.reset()

        then:
        metrics.totalMessages() == 0
        metrics.totalErrors() == 0
        metrics.messagesForChannel("telegram") == 0
    }
}
