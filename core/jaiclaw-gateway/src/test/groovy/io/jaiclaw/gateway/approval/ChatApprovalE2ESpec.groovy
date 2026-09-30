package io.jaiclaw.gateway.approval

import io.jaiclaw.channel.ChannelAdapter
import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.channel.ChannelRegistry
import io.jaiclaw.channel.DeliveryResult
import io.jaiclaw.core.agent.ApprovalPolicy
import io.jaiclaw.core.agent.PendingApprovalRegistry
import io.jaiclaw.core.agent.ToolApprovalDecision
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The round trip: a tool call asks for approval, the approver replies in chat,
 * the waiting future resolves.
 *
 * <p>Exercises {@link ChatApprovalHandler} and {@link ApprovalReplyFilter}
 * against a real {@link PendingApprovalRegistry}, because the correlation
 * between them is the whole feature — either half alone proves nothing.
 */
class ChatApprovalE2ESpec extends Specification {

    static final Approver OWNER = new Approver("telegram", "bot-1", "9001")

    PendingApprovalRegistry registry = new PendingApprovalRegistry()
    ChannelAdapter adapter = Mock(ChannelAdapter)
    ChannelRegistry channels = Mock(ChannelRegistry)
    ChannelMessageHandler gateway = Mock(ChannelMessageHandler)

    ChatApprovalHandler handler
    ApprovalReplyFilter filter

    def setup() {
        channels.get("telegram") >> Optional.of(adapter)
        adapter.sendMessage(_) >> new DeliveryResult.Success("mid-1")
        handler = new ChatApprovalHandler(channels, registry,
                new ConfiguredApproverResolver([OWNER]), ApprovalPolicy.DEFAULT)
        filter = new ApprovalReplyFilter(registry)
        filter.setDownstream(gateway)
    }

    private ChannelMessage replyFrom(String peerId, String text) {
        ChannelMessage.inbound("u1", "telegram", "bot-1", peerId, text, [], [:])
    }

    def "a yes from the approver approves the waiting tool call"() {
        given:
        def future = handler.requestApproval("rebootDevice", [host: "pixoo"], "sess-1")

        expect: "the caller is still waiting"
        !future.isDone()

        when: "the approver replies in chat"
        filter.onMessage(replyFrom("9001", "yes"))

        then:
        future.get(1, TimeUnit.SECONDS) instanceof ToolApprovalDecision.Approved

        and: "the reply is consumed, not handled as a new agent turn"
        0 * gateway.onMessage(_)
    }

    def "a no denies the waiting tool call"() {
        given:
        def future = handler.requestApproval("rebootDevice", [:], "sess-1")

        when:
        filter.onMessage(replyFrom("9001", "no"))

        then:
        def decision = future.get(1, TimeUnit.SECONDS)
        decision instanceof ToolApprovalDecision.Denied
        ((ToolApprovalDecision.Denied) decision).reason().contains("approver")

        and:
        0 * gateway.onMessage(_)
    }

    def "the question is delivered to the configured approver, not the requester"() {
        given: "a session triggered by a different user entirely"
        ChannelMessage sent = null

        when:
        handler.requestApproval("rebootDevice", [host: "pixoo"], "agent:telegram:bot-1:7777")

        then:
        1 * adapter.sendMessage(_ as ChannelMessage) >> { ChannelMessage m ->
            sent = m
            new DeliveryResult.Success("mid-1")
        }

        and: "asked of the owner, not of peer 7777 who triggered the run"
        sent.peerId() == "9001"

        and: "and it says what is being asked and what silence means"
        sent.content().contains("rebootDevice")
        sent.content().contains("host: pixoo")
        sent.content().toLowerCase().contains("yes")
        sent.content().contains("denied")
    }

    def "a reply from someone who is not the approver does not approve"() {
        given:
        def future = handler.requestApproval("rebootDevice", [:], "sess-1")

        when: "a different chat says yes"
        filter.onMessage(replyFrom("1234", "yes"))

        then: "the tool call is untouched"
        !future.isDone()

        and: "and their message is an ordinary turn"
        1 * gateway.onMessage(_ as ChannelMessage)
    }

    def "a non-answer passes through and leaves the request open"() {
        given:
        def future = handler.requestApproval("rebootDevice", [:], "sess-1")

        when: "the approver asks a clarifying question instead of answering"
        filter.onMessage(replyFrom("9001", "what does that do?"))

        then: "they get an answer from the agent rather than silence"
        1 * gateway.onMessage(_ as ChannelMessage)

        and: "and the approval is still waiting"
        !future.isDone()

        when: "they then answer properly"
        filter.onMessage(replyFrom("9001", "yes"))

        then:
        future.get(1, TimeUnit.SECONDS) instanceof ToolApprovalDecision.Approved
    }

    def "a second yes does not approve a later tool call"() {
        given:
        def first = handler.requestApproval("rebootDevice", [:], "sess-1")
        filter.onMessage(replyFrom("9001", "yes"))
        first.get(1, TimeUnit.SECONDS)

        when: "a new tool call asks, and the earlier yes is repeated"
        def second = handler.requestApproval("dropDatabase", [:], "sess-1")

        then: "it waits on its own answer"
        !second.isDone()
    }

    def "with no approver configured the call is denied, not allowed"() {
        given: "a deployment that enabled chat approval but named nobody"
        def noOne = new ChatApprovalHandler(channels, registry,
                new ConfiguredApproverResolver([]), ApprovalPolicy.DEFAULT)

        when:
        def future = noOne.requestApproval("rebootDevice", [:], "sess-1")

        then: "resolved immediately as a denial — fail closed"
        future.isDone()
        def decision = future.get()
        decision instanceof ToolApprovalDecision.Denied
        ((ToolApprovalDecision.Denied) decision).reason().contains("no approver")
    }

    def "an unregistered approver channel denies rather than hanging"() {
        given:
        def brokenChannels = Mock(ChannelRegistry)
        brokenChannels.get("telegram") >> Optional.empty()
        def broken = new ChatApprovalHandler(brokenChannels, registry,
                new ConfiguredApproverResolver([OWNER]), ApprovalPolicy.DEFAULT)

        when:
        def future = broken.requestApproval("rebootDevice", [:], "sess-1")

        then:
        future.isDone()
        future.get() instanceof ToolApprovalDecision.Denied
    }

    def "a send failure denies instead of leaving the caller to wait out the window"() {
        given:
        def failingAdapter = Mock(ChannelAdapter)
        def failingChannels = Mock(ChannelRegistry)
        failingChannels.get("telegram") >> Optional.of(failingAdapter)
        failingAdapter.sendMessage(_) >> { throw new IllegalStateException("telegram down") }
        def h = new ChatApprovalHandler(failingChannels, registry,
                new ConfiguredApproverResolver([OWNER]), ApprovalPolicy.DEFAULT)

        when:
        def future = h.requestApproval("rebootDevice", [:], "sess-1")

        then: "the question never arrived, so waiting would be pointless"
        future.isDone()
        future.get() instanceof ToolApprovalDecision.Denied

        and: "and nothing is left registered for a late reply to redeem"
        registry.size() == 0
    }

    def "an expired request is not approved by a late yes"() {
        given: "a one-millisecond window"
        def impatient = new ChatApprovalHandler(channels, registry,
                new ConfiguredApproverResolver([OWNER]),
                new ApprovalPolicy(false, Duration.ofMillis(1),
                        ApprovalPolicy.OnTimeout.DENY, [:]))
        def future = impatient.requestApproval("rebootDevice", [:], "sess-1")
        Thread.sleep(25)

        when: "the approver replies after the window closed"
        filter.onMessage(replyFrom("9001", "yes"))

        then: "the stale answer does not authorise the call"
        !future.isDone()

        and: "and it is treated as an ordinary message"
        1 * gateway.onMessage(_ as ChannelMessage)
    }

    def "the prompt states the timeout action when it is approve"() {
        given:
        ChannelMessage sent = null
        def lenient = new ChatApprovalHandler(channels, registry,
                new ConfiguredApproverResolver([OWNER]),
                new ApprovalPolicy(false, Duration.ofMinutes(5),
                        ApprovalPolicy.OnTimeout.APPROVE, [:]))

        when:
        lenient.requestApproval("harmless", [:], "sess-1")

        then:
        1 * adapter.sendMessage(_ as ChannelMessage) >> { ChannelMessage m ->
            sent = m
            new DeliveryResult.Success("mid-1")
        }
        sent.content().contains("proceed automatically")
    }
}
