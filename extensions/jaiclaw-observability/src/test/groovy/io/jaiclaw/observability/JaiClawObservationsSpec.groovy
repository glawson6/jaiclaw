package io.jaiclaw.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import spock.lang.Specification

class JaiClawObservationsSpec extends Specification {

    def "observation names are correct"() {
        expect:
        JaiClawObservations.AGENT_INVOCATION == "jaiclaw.agent.invocation"
        JaiClawObservations.TOOL_CALL == "jaiclaw.tool.call"
        JaiClawObservations.CHANNEL_MESSAGE == "jaiclaw.channel.message"
    }

    def "metric names are correct"() {
        expect:
        JaiClawObservations.METRIC_AGENT_INVOCATIONS == "jaiclaw.agent.invocations"
        JaiClawObservations.METRIC_TOOL_CALLS == "jaiclaw.tool.calls"
        JaiClawObservations.METRIC_CHANNEL_MESSAGES == "jaiclaw.channel.messages"
        JaiClawObservations.METRIC_TOKEN_USAGE == "jaiclaw.tokens.usage"
        JaiClawObservations.METRIC_SESSIONS_ACTIVE == "jaiclaw.sessions.active"
    }

    def "agentInvocation creates valid observation"() {
        given:
        def registry = ObservationRegistry.create()

        when:
        def obs = JaiClawObservations.agentInvocation(registry, "agent1", "telegram")
        obs.start()
        obs.stop()

        then:
        noExceptionThrown()
    }

    def "toolCall creates valid observation"() {
        given:
        def registry = ObservationRegistry.create()

        when:
        def obs = JaiClawObservations.toolCall(registry, "web_fetch")
        obs.start()
        obs.stop()

        then:
        noExceptionThrown()
    }

    def "channelMessage creates valid observation"() {
        given:
        def registry = ObservationRegistry.create()

        when:
        def obs = JaiClawObservations.channelMessage(registry, "slack", "inbound")
        obs.start()
        obs.stop()

        then:
        noExceptionThrown()
    }

    def "agentInvocation handles null channelId"() {
        given:
        def registry = ObservationRegistry.create()

        when:
        def obs = JaiClawObservations.agentInvocation(registry, "agent1", null)
        obs.start()
        obs.stop()

        then:
        noExceptionThrown()
    }

    def "JaiClawMetrics records agent invocation counter"() {
        given:
        def meterRegistry = new SimpleMeterRegistry()
        def metrics = new JaiClawMetrics(meterRegistry)

        when:
        metrics.recordAgentInvocation("agent1", "telegram")
        metrics.recordAgentInvocation("agent1", "telegram")
        metrics.recordAgentInvocation("agent1", "slack")

        then:
        def counter = meterRegistry.find(JaiClawObservations.METRIC_AGENT_INVOCATIONS)
                .tag("agentId", "agent1")
                .tag("channelId", "telegram")
                .counter()
        counter != null
        counter.count() == 2.0d
    }

    def "JaiClawMetrics records tool call timer"() {
        given:
        def meterRegistry = new SimpleMeterRegistry()
        def metrics = new JaiClawMetrics(meterRegistry)

        when:
        metrics.recordToolCall("web_fetch", true, 150)
        metrics.recordToolCall("web_fetch", false, 50)

        then:
        def successTimer = meterRegistry.find(JaiClawObservations.METRIC_TOOL_CALLS)
                .tag("toolName", "web_fetch")
                .tag("outcome", "success")
                .timer()
        successTimer != null
        successTimer.count() == 1L
    }

    def "JaiClawMetrics records channel messages"() {
        given:
        def meterRegistry = new SimpleMeterRegistry()
        def metrics = new JaiClawMetrics(meterRegistry)

        when:
        metrics.recordChannelMessage("telegram", "inbound")
        metrics.recordChannelMessage("telegram", "outbound")

        then:
        def inbound = meterRegistry.find(JaiClawObservations.METRIC_CHANNEL_MESSAGES)
                .tag("channelId", "telegram")
                .tag("direction", "inbound")
                .counter()
        inbound != null
        inbound.count() == 1.0d
    }

    def "JaiClawMetrics records token usage"() {
        given:
        def meterRegistry = new SimpleMeterRegistry()
        def metrics = new JaiClawMetrics(meterRegistry)

        when:
        metrics.recordTokenUsage("agent1", "claude-sonnet-4-5", "input", 500)

        then:
        def counter = meterRegistry.find(JaiClawObservations.METRIC_TOKEN_USAGE)
                .tag("agentId", "agent1")
                .tag("model", "claude-sonnet-4-5")
                .tag("tokenType", "input")
                .counter()
        counter != null
        counter.count() == 500.0d
    }
}
