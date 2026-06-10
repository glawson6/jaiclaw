package io.jaiclaw.channel

import io.jaiclaw.channel.chunking.PlatformLimits
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 0.8.0 P3.3: locks the consolidated lifecycle / chunking / dispatch
 * behavior of {@link AbstractChannelAdapter}.
 */
class AbstractChannelAdapterSpec extends Specification {

    /** Test adapter that records doStart / doStop / doSend invocations. */
    static class TestAdapter extends AbstractChannelAdapter {
        final List<ChannelMessage> sent = new CopyOnWriteArrayList<>()
        int startCalls = 0
        int stopCalls = 0
        boolean failNextSend = false

        TestAdapter(PlatformLimits limits = PlatformLimits.DEFAULT) {
            super("test", "Test", limits)
        }

        @Override
        protected void doStart() { startCalls++ }

        @Override
        protected void doStop() { stopCalls++ }

        @Override
        protected DeliveryResult doSend(ChannelMessage message) {
            if (failNextSend) {
                failNextSend = false
                return new DeliveryResult.Failure("forced", "forced", false)
            }
            sent.add(message)
            return new DeliveryResult.Success("msg-" + sent.size())
        }

        void pushInbound(ChannelMessage m) { dispatchInbound(m) }
    }

    private ChannelMessage outbound(String content) {
        new ChannelMessage("id", "test", "acct", "peer", content,
                Instant.now(), ChannelMessage.Direction.OUTBOUND, [], [:])
    }

    def "constructor rejects blank channelId / displayName"() {
        when:
        new AbstractChannelAdapter(arg1, arg2, PlatformLimits.DEFAULT) {
            @Override protected void doStart() {}
            @Override protected void doStop() {}
            @Override protected DeliveryResult doSend(ChannelMessage m) { null }
        }

        then:
        thrown(IllegalArgumentException)

        where:
        arg1    | arg2
        null    | "X"
        ""      | "X"
        "x"     | null
        "x"     | ""
    }

    def "lifecycle: start sets running=true, stop sets running=false"() {
        given:
        TestAdapter a = new TestAdapter()

        expect:
        !a.running

        when:
        a.start({ msg -> })

        then:
        a.running
        a.startCalls == 1

        when:
        a.stop()

        then:
        !a.running
        a.stopCalls == 1
    }

    def "start is idempotent — second call is a no-op"() {
        given:
        TestAdapter a = new TestAdapter()

        when:
        a.start({ msg -> })
        a.start({ msg -> })

        then:
        a.startCalls == 1
    }

    def "stop is idempotent — second call is a no-op"() {
        given:
        TestAdapter a = new TestAdapter()
        a.start({ msg -> })

        when:
        a.stop()
        a.stop()

        then:
        a.stopCalls == 1
    }

    def "sendMessage before start returns adapter_not_running failure"() {
        given:
        TestAdapter a = new TestAdapter()

        when:
        DeliveryResult result = a.sendMessage(outbound("hi"))

        then:
        result instanceof DeliveryResult.Failure
        ((DeliveryResult.Failure) result).errorCode() == "adapter_not_running"
        a.sent.isEmpty()
    }

    def "sendMessage with a null message returns null_message failure"() {
        given:
        TestAdapter a = new TestAdapter()
        a.start({ msg -> })

        when:
        DeliveryResult result = a.sendMessage(null)

        then:
        result instanceof DeliveryResult.Failure
        ((DeliveryResult.Failure) result).errorCode() == "null_message"
    }

    def "sendMessage delegates to doSend when content fits the platform limit"() {
        given:
        TestAdapter a = new TestAdapter(new PlatformLimits(100))
        a.start({ msg -> })

        when:
        DeliveryResult result = a.sendMessage(outbound("short"))

        then:
        result instanceof DeliveryResult.Success
        a.sent.size() == 1
        a.sent[0].content() == "short"
    }

    def "sendMessage chunks long content and calls doSend once per chunk"() {
        given:
        TestAdapter a = new TestAdapter(new PlatformLimits(10))
        a.start({ msg -> })
        String content = "a" * 35       // 35 chars / 10 cap → 4 chunks

        when:
        DeliveryResult result = a.sendMessage(outbound(content))

        then:
        result instanceof DeliveryResult.Success
        a.sent.size() >= 2              // at least 2 chunks
        a.sent.every { it.content().length() <= 10 }
        a.sent*.content().join("") == content
    }

    def "sendMessage on chunked content returns the first chunk's id"() {
        given:
        TestAdapter a = new TestAdapter(new PlatformLimits(10))
        a.start({ msg -> })

        when:
        DeliveryResult result = a.sendMessage(outbound("a" * 35))

        then:
        result instanceof DeliveryResult.Success
        ((DeliveryResult.Success) result).platformMessageId() == "msg-1"
    }

    def "dispatchInbound forwards to the registered handler"() {
        given:
        TestAdapter a = new TestAdapter()
        List received = []
        a.start({ msg -> received.add(msg) })
        ChannelMessage inbound = new ChannelMessage("in1", "test", "acct", "peer",
                "hello", Instant.now(), ChannelMessage.Direction.INBOUND, [], [:])

        when:
        a.pushInbound(inbound)

        then:
        received.size() == 1
        received[0].is(inbound)
    }

    def "dispatchInbound is a no-op before start"() {
        given:
        TestAdapter a = new TestAdapter()
        ChannelMessage inbound = new ChannelMessage("in1", "test", "acct", "peer",
                "hello", Instant.now(), ChannelMessage.Direction.INBOUND, [], [:])

        when:
        a.pushInbound(inbound)

        then:
        noExceptionThrown()
    }

    def "dispatchInbound is a no-op after stop"() {
        given:
        TestAdapter a = new TestAdapter()
        List received = []
        a.start({ msg -> received.add(msg) })
        a.stop()

        when:
        a.pushInbound(new ChannelMessage("in1", "test", "acct", "peer",
                "hello", Instant.now(), ChannelMessage.Direction.INBOUND, [], [:]))

        then:
        received.isEmpty()
    }

    def "doStart exception unwinds running state"() {
        given:
        AbstractChannelAdapter a = new AbstractChannelAdapter("test", "Test", PlatformLimits.DEFAULT) {
            @Override protected void doStart() { throw new RuntimeException("nope") }
            @Override protected void doStop() {}
            @Override protected DeliveryResult doSend(ChannelMessage m) { null }
        }

        when:
        a.start({ msg -> })

        then:
        thrown(RuntimeException)
        !a.running
    }

    def "exposes identity fields via final getters"() {
        given:
        TestAdapter a = new TestAdapter(PlatformLimits.SLACK)

        expect:
        a.channelId() == "test"
        a.displayName() == "Test"
        a.platformLimits() == PlatformLimits.SLACK
    }
}
