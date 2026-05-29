package io.jaiclaw.observability

import io.jaiclaw.core.hook.HookHandler
import io.jaiclaw.core.hook.HookName
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.jaiclaw.plugin.PluginApi
import spock.lang.Specification

class ObservabilityPluginSpec extends Specification {

    def meterRegistry = new SimpleMeterRegistry()
    def metrics = new JaiClawMetrics(meterRegistry)
    def plugin = new ObservabilityPlugin(metrics)
    Map<HookName, HookHandler> handlers = [:]

    def setup() {
        def api = Mock(PluginApi)
        api.on(_, _) >> { HookName name, HookHandler handler -> handlers[name] = handler }
        api.on(_, _, _) >> { HookName name, HookHandler handler, int priority -> handlers[name] = handler }
        plugin.register(api)
    }

    def "records agent invocation metric on BEFORE_AGENT_START hook"() {
        when:
        handlers[HookName.BEFORE_AGENT_START].handle([agentId: "agent1", channelId: "telegram"], null)

        then:
        def counter = meterRegistry.find(JaiClawObservations.METRIC_AGENT_INVOCATIONS)
                .tag("agentId", "agent1")
                .tag("channelId", "telegram")
                .counter()
        counter != null
        counter.count() == 1.0d
    }

    def "records tool call metric on BEFORE_TOOL_CALL and AFTER_TOOL_CALL hooks"() {
        when:
        handlers[HookName.BEFORE_TOOL_CALL].handle([toolName: "web_fetch"], null)
        handlers[HookName.AFTER_TOOL_CALL].handle([toolName: "web_fetch", success: true], null)

        then:
        def timer = meterRegistry.find(JaiClawObservations.METRIC_TOOL_CALLS)
                .tag("toolName", "web_fetch")
                .tag("outcome", "success")
                .timer()
        timer != null
        timer.count() == 1L
    }

    def "records channel message metrics on MESSAGE_RECEIVED and MESSAGE_SENT hooks"() {
        when:
        handlers[HookName.MESSAGE_RECEIVED].handle([channelId: "slack"], null)
        handlers[HookName.MESSAGE_SENT].handle([channelId: "slack"], null)

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
