package io.jaiclaw.observability

import io.jaiclaw.core.hook.HookHandler
import io.jaiclaw.core.hook.event.AgentStartedEvent
import io.jaiclaw.core.hook.event.HookEvent
import io.jaiclaw.core.hook.event.MessageReceivedEvent
import io.jaiclaw.core.hook.event.MessageSentEvent
import io.jaiclaw.core.hook.event.ToolCallEndedEvent
import io.jaiclaw.core.hook.event.ToolCallStartedEvent
import io.jaiclaw.plugin.PluginApi
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

/**
 * 0.8.0 hard-break: ObservabilityPlugin now registers handlers against
 * typed event classes instead of {@code HookName} enum values. The casting
 * helpers ({@code extractString}/{@code extractBoolean}) are gone — the
 * handler just reads fields off the typed record. See
 * {@code docs/MIGRATION-0.8.md} § P3.1.
 */
class ObservabilityPluginSpec extends Specification {

    def meterRegistry = new SimpleMeterRegistry()
    def metrics = new JaiClawMetrics(meterRegistry)
    def plugin = new ObservabilityPlugin(metrics)
    Map<Class<? extends HookEvent>, HookHandler> handlers = [:]

    def setup() {
        def api = Mock(PluginApi)
        api.on(_, _) >> { Class type, HookHandler handler -> handlers[type] = handler }
        api.on(_, _, _) >> { Class type, HookHandler handler, int priority -> handlers[type] = handler }
        plugin.register(api)
    }

    def "records agent invocation metric on AgentStartedEvent"() {
        when:
        // sessionKey format: agentId:channel:accountId:peerId — observability
        // extracts the channel segment for the metric tag.
        handlers[AgentStartedEvent.class].handle(
                AgentStartedEvent.of("agent1", "agent1:telegram:acct:peer", "hi"))

        then:
        def counter = meterRegistry.find(JaiClawObservations.METRIC_AGENT_INVOCATIONS)
                .tag("agentId", "agent1")
                .tag("channelId", "telegram")
                .counter()
        counter != null
        counter.count() == 1.0d
    }

    def "records tool call metric on ToolCallStartedEvent and ToolCallEndedEvent"() {
        when:
        handlers[ToolCallStartedEvent.class].handle(
                ToolCallStartedEvent.of("default", "sess", "web_fetch", "{}", 1))
        handlers[ToolCallEndedEvent.class].handle(
                ToolCallEndedEvent.of("default", "sess", "web_fetch", "{}", "ok", 1))

        then:
        def timer = meterRegistry.find(JaiClawObservations.METRIC_TOOL_CALLS)
                .tag("toolName", "web_fetch")
                .tag("outcome", "success")
                .timer()
        timer != null
        timer.count() == 1L
    }

    def "records channel message metrics on MessageReceivedEvent and MessageSentEvent"() {
        when:
        handlers[MessageReceivedEvent.class].handle(
                MessageReceivedEvent.of("default", "sess", "slack", "acct", "peer", "hi"))
        handlers[MessageSentEvent.class].handle(
                MessageSentEvent.of("default", "sess", "slack", "acct", "peer", "bye", true))

        then:
        def inbound = meterRegistry.find(JaiClawObservations.METRIC_CHANNEL_MESSAGES)
                .tag("channelId", "slack")
                .tag("direction", "inbound")
                .counter()
        inbound != null
        inbound.count() == 1.0d

        and:
        def outbound = meterRegistry.find(JaiClawObservations.METRIC_CHANNEL_MESSAGES)
                .tag("channelId", "slack")
                .tag("direction", "outbound")
                .counter()
        outbound != null
        outbound.count() == 1.0d
    }
}
