package io.jaiclaw.gateway

import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.channel.ChannelRegistry
import spock.lang.Specification

/**
 * Filter chaining.
 *
 * <p>Before 1.3.0 the lifecycle took a single filter and the auto-configuration
 * had to hand it the GatewayService as its downstream. A second
 * {@code GatewayMessageFilter} bean broke bean resolution outright, which
 * blocked adding an approval-reply filter alongside the existing Telegram
 * authorization filter. The lifecycle now owns chain construction.
 */
class FilteredGatewayLifecycleSpec extends Specification {

    /** Records what it received and forwards, so order is observable. */
    static class RecordingFilter implements GatewayMessageFilter {
        final String name
        final List<String> log
        final boolean consume
        ChannelMessageHandler downstream

        RecordingFilter(String name, List<String> log, boolean consume = false) {
            this.name = name
            this.log = log
            this.consume = consume
        }

        @Override
        void setDownstream(ChannelMessageHandler downstream) {
            this.downstream = downstream
        }

        @Override
        void onMessage(ChannelMessage message) {
            log << name
            if (!consume) {
                downstream.onMessage(message)
            }
        }
    }

    private ChannelMessage inbound() {
        ChannelMessage.inbound("u1", "telegram", "acct", "peer", "hello", [], [:])
    }

    def "filters run in the supplied order and the tail reaches the gateway"() {
        given:
        def order = []
        def first = new RecordingFilter("first", order)
        def second = new RecordingFilter("second", order)
        def gatewayService = Mock(GatewayService)
        def registry = Mock(ChannelRegistry)

        ChannelMessageHandler head = null
        def lifecycle = new FilteredGatewayLifecycle(
                gatewayService, registry, [first, second])

        when:
        lifecycle.start()

        then: "adapters are started with the head of the chain, not the gateway"
        1 * registry.startAll(_ as ChannelMessageHandler) >> { ChannelMessageHandler h -> head = h }
        1 * registry.size() >> 1
        head.is(first)

        when: "a message enters at the head"
        head.onMessage(inbound())

        then: "both filters ran, in order, and the gateway received it"
        order == ["first", "second"]
        1 * gatewayService.onMessage(_ as ChannelMessage)
    }

    def "a filter that consumes a message stops the chain"() {
        given: "the second filter consumes — this is what approval-reply matching does"
        def order = []
        def first = new RecordingFilter("first", order)
        def consuming = new RecordingFilter("consuming", order, true)
        def gatewayService = Mock(GatewayService)
        def registry = Stub(ChannelRegistry)
        def lifecycle = new FilteredGatewayLifecycle(
                gatewayService, registry, [first, consuming])
        lifecycle.start()

        when: "the chain head is the first filter"
        first.onMessage(inbound())

        then:
        order == ["first", "consuming"]

        and: "the message never becomes an agent turn"
        0 * gatewayService.onMessage(_)
    }

    def "single-filter constructor still wires the gateway as downstream"() {
        given: "the pre-1.3.0 call shape"
        def order = []
        def only = new RecordingFilter("only", order)
        def gatewayService = Mock(GatewayService)
        def registry = Mock(ChannelRegistry)

        ChannelMessageHandler head = null
        def lifecycle = new FilteredGatewayLifecycle(gatewayService, registry, only)

        when:
        lifecycle.start()

        then:
        1 * registry.startAll(_ as ChannelMessageHandler) >> { ChannelMessageHandler h -> head = h }
        1 * registry.size() >> 0
        head.is(only)

        when:
        head.onMessage(inbound())

        then:
        order == ["only"]
        1 * gatewayService.onMessage(_ as ChannelMessage)
    }

    def "an empty filter list sends adapters straight to the gateway"() {
        given:
        def gatewayService = Mock(GatewayService)
        def registry = Mock(ChannelRegistry)
        ChannelMessageHandler head = null
        def lifecycle = new FilteredGatewayLifecycle(gatewayService, registry, [])

        when:
        lifecycle.start()

        then:
        1 * registry.startAll(_ as ChannelMessageHandler) >> { ChannelMessageHandler h -> head = h }
        1 * registry.size() >> 0
        head.is(gatewayService)
    }

    def "every filter has a downstream before any message is dispatched"() {
        given: "chain is built back-to-front so no filter briefly points at nothing"
        def order = []
        def a = new RecordingFilter("a", order)
        def b = new RecordingFilter("b", order)
        def c = new RecordingFilter("c", order)
        def registry = Mock(ChannelRegistry)
        def lifecycle = new FilteredGatewayLifecycle(
                Mock(GatewayService), registry, [a, b, c])

        when:
        lifecycle.start()

        then:
        1 * registry.startAll(_)
        1 * registry.size() >> 0

        and:
        a.downstream.is(b)
        b.downstream.is(c)
        c.downstream != null
    }
}
