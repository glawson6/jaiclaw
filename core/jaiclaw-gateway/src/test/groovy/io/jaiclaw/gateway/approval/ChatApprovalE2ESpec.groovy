package io.jaiclaw.gateway.approval

import io.jaiclaw.channel.ChannelAdapter
import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.channel.ChannelRegistry
import io.jaiclaw.channel.DeliveryResult
import io.jaiclaw.core.agent.ApprovalPolicy
import io.jaiclaw.core.agent.PendingApprovalRegistry
import io.jaiclaw.core.agent.ToolApprovalDecision
import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * The round trip: a tool call asks for approval, the approver replies in chat,
 * the waiting future resolves.
 *
 * <p>Exercises {@link ChatApprovalHandler} and {@link ApprovalReplyFilter}
 * against a real {@link PendingApprovalRegistry}, because the correlation
 * between them is the whole feature — either half alone proves nothing.
 *
 * <p>Several specs here are the 2026-10-01 security review's findings, pinned:
 * tenant-context mismatch, approval by a non-approver in a group, confirmation
 * hijack via supersede, and the over-broad vocabulary.
 */
class ChatApprovalE2ESpec extends Specification {

    static final Approver OWNER = new Approver("telegram", "bot-1", "9001", null)
    static final Approver OWNER_IN_GROUP = new Approver("telegram", "bot-1", "-100777", "9001")

    PendingApprovalRegistry registry = new PendingApprovalRegistry()
    ChannelAdapter adapter = Mock(ChannelAdapter)
    ChannelRegistry channels = Mock(ChannelRegistry)
    ChannelMessageHandler gateway = Mock(ChannelMessageHandler)
    List<ChannelMessage> outbound = []

    ChatApprovalHandler handler
    ApprovalReplyFilter filter

    def setup() {
        channels.get("telegram") >> Optional.of(adapter)
        adapter.sendMessage(_ as ChannelMessage) >> { ChannelMessage m ->
            outbound << m
            new DeliveryResult.Success("mid-${outbound.size()}")
        }
        wire([OWNER])
    }

    def cleanup() {
        TenantContextHolder.clear()
    }

    private void wire(List<Approver> approvers) {
        def resolver = new ConfiguredApproverResolver(approvers)
        handler = new ChatApprovalHandler(channels, registry, resolver, ApprovalPolicy.DEFAULT)
        filter = new ApprovalReplyFilter(registry, resolver, channels)
        filter.setDownstream(gateway)
    }

    private static ChannelMessage replyFrom(String peerId, String text, String senderId = peerId) {
        ChannelMessage.inbound("u1", "telegram", "bot-1", peerId, text, [],
                senderId == null ? [:] : [sender_id: senderId])
    }

    /** The code the approver would read off the most recent prompt. */
    private String lastCode() {
        def m = (outbound.last().content() =~ /code ([A-Z0-9]{4})/)
        assert m.find()
        m.group(1)
    }

    def "a yes with the code approves the waiting tool call"() {
        given:
        def future = handler.requestApproval("rebootDevice", [host: "pixoo"], "sess-1")

        expect: "the caller is still waiting"
        !future.isDone()

        when: "the approver replies in chat, quoting the code from the prompt"
        filter.onMessage(replyFrom("9001", "yes ${lastCode()}"))

        then:
        future.get(1, TimeUnit.SECONDS) instanceof ToolApprovalDecision.Approved

        and: "the reply is consumed, not handled as a new agent turn"
        0 * gateway.onMessage(_)
    }

    def "a no with the code denies the waiting tool call"() {
        given:
        def future = handler.requestApproval("rebootDevice", [:], "sess-1")

        when:
        filter.onMessage(replyFrom("9001", "no ${lastCode()}"))

        then:
        def decision = future.get(1, TimeUnit.SECONDS)
        decision instanceof ToolApprovalDecision.Denied
        ((ToolApprovalDecision.Denied) decision).reason().contains("approver")

        and:
        0 * gateway.onMessage(_)
    }

    def "a bare yes redeems nothing and the approver is told what to include"() {
        given:
        def future = handler.requestApproval("rebootDevice", [:], "sess-1")
        def code = lastCode()

        when: "the approver answers without the code"
        filter.onMessage(replyFrom("9001", "yes"))

        then: "the tool call is still waiting — never guess which question was answered"
        !future.isDone()

        and: "the message is consumed rather than becoming an agent turn"
        0 * gateway.onMessage(_)

        and: "and the approver is told the open code so they can answer precisely"
        outbound.size() == 2
        outbound.last().peerId() == "9001"
        outbound.last().content().contains(code)
        outbound.last().content().contains("rebootDevice")

        when: "they answer properly"
        filter.onMessage(replyFrom("9001", "yes $code"))

        then:
        future.get(1, TimeUnit.SECONDS) instanceof ToolApprovalDecision.Approved
    }

    def "the question is delivered to the configured approver, not the requester, with a code"() {
        when:
        handler.requestApproval("rebootDevice", [host: "pixoo"], "agent:telegram:bot-1:7777")

        then: "asked of the owner, not of peer 7777 who triggered the run"
        outbound.size() == 1
        outbound[0].peerId() == "9001"

        and: "and it says what is being asked, how to answer, and what silence means"
        outbound[0].content().contains("rebootDevice")
        outbound[0].content().contains("host: pixoo")
        outbound[0].content().contains("yes ${lastCode()}")
        outbound[0].content().contains("no ${lastCode()}")
        outbound[0].content().contains("denied")
    }

    // --- finding 1.1: tenant context ---

    def "the round trip works in multi-tenant mode even though the filter runs before tenant resolution"() {
        given: "the handler runs on the agent thread, where tenant context is set"
        TenantContextHolder.set(new DefaultTenantContext("acme", "acme"))
        def future = handler.requestApproval("rebootDevice", [:], "sess-1")
        def code = lastCode()

        when: "the reply arrives through the filter, upstream of tenant resolution"
        TenantContextHolder.clear()
        filter.onMessage(replyFrom("9001", "yes $code"))

        then: "it matches — the key does not depend on the tenant"
        future.get(1, TimeUnit.SECONDS) instanceof ToolApprovalDecision.Approved
    }

    def "the requesting tenant is recorded on the pending request for audit"() {
        given:
        TenantContextHolder.set(new DefaultTenantContext("acme", "acme"))
        handler.requestApproval("rebootDevice", [:], "sess-1")

        expect:
        registry.find(lastCode()).get().tenantId() == "acme"
    }

    // --- finding 1.2: approver identity ---

    def "in a group chat, a yes from someone other than the configured approver does not approve"() {
        given: "the approver is asked in a team channel, restricted to their user id"
        wire([OWNER_IN_GROUP])
        def future = handler.requestApproval("rebootDevice", [:], "sess-1")
        def code = lastCode()

        when: "another member of the group replies with the right code"
        filter.onMessage(replyFrom("-100777", "yes $code", "4242"))

        then: "the tool call is untouched"
        !future.isDone()

        and: "and their message is an ordinary turn, not consumed"
        1 * gateway.onMessage(_ as ChannelMessage)

        when: "the configured approver replies"
        filter.onMessage(replyFrom("-100777", "yes $code", "9001"))

        then:
        future.get(1, TimeUnit.SECONDS) instanceof ToolApprovalDecision.Approved
    }

    def "a user-id restriction fails closed when the channel exposes no sender id"() {
        given:
        wire([OWNER_IN_GROUP])
        def future = handler.requestApproval("rebootDevice", [:], "sess-1")

        when: "a reply arrives with no sender_id in platformData"
        filter.onMessage(replyFrom("-100777", "yes ${lastCode()}", null))

        then: "it cannot be attributed, so it does not count"
        !future.isDone()
        1 * gateway.onMessage(_ as ChannelMessage)
    }

    def "a reply from a different conversation does not approve, even with the code"() {
        given:
        def future = handler.requestApproval("rebootDevice", [:], "sess-1")

        when: "a different chat says yes and somehow knows the code"
        filter.onMessage(replyFrom("1234", "yes ${lastCode()}"))

        then: "the tool call is untouched"
        !future.isDone()

        and: "and their message is an ordinary turn"
        1 * gateway.onMessage(_ as ChannelMessage)
    }

    // --- finding 1.3 / 1.4: confirmation hijack and supersede ---

    def "an approval meant for a harmless call cannot be applied to a dangerous one that arrived later"() {
        given: "the approver is looking at a wiki_read prompt"
        def wiki = handler.requestApproval("wiki_read", [page: "runbook"], "sess-1")
        def wikiCode = lastCode()

        and: "before they reply, a prompt-injected model asks for shell_exec"
        def shell = handler.requestApproval("shell_exec", [cmd: "rm -rf /"], "sess-1")
        def shellCode = lastCode()

        expect: "the first request was NOT displaced"
        !wiki.isDone()
        !shell.isDone()
        wikiCode != shellCode

        when: "the approver approves what they were looking at"
        filter.onMessage(replyFrom("9001", "yes $wikiCode"))

        then: "exactly that call proceeds"
        wiki.get(1, TimeUnit.SECONDS) instanceof ToolApprovalDecision.Approved
        !shell.isDone()

        when: "and they deny the other"
        filter.onMessage(replyFrom("9001", "no $shellCode"))

        then:
        shell.get(1, TimeUnit.SECONDS) instanceof ToolApprovalDecision.Denied
    }

    // --- finding 1.5: vocabulary ---

    def "an incidental ok in the approver chat does not approve anything"() {
        given:
        def future = handler.requestApproval("rebootDevice", [:], "sess-1")

        when:
        filter.onMessage(replyFrom("9001", "ok"))

        then: "not an answer at all — passed through, request still open"
        !future.isDone()
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
        filter.onMessage(replyFrom("9001", "yes ${lastCode()}"))

        then:
        future.get(1, TimeUnit.SECONDS) instanceof ToolApprovalDecision.Approved
    }

    def "a repeated yes with a spent code does not approve a later tool call"() {
        given:
        def first = handler.requestApproval("rebootDevice", [:], "sess-1")
        def firstCode = lastCode()
        filter.onMessage(replyFrom("9001", "yes $firstCode"))
        first.get(1, TimeUnit.SECONDS)

        when: "a new tool call asks, and the earlier answer is repeated verbatim"
        def second = handler.requestApproval("dropDatabase", [:], "sess-1")
        filter.onMessage(replyFrom("9001", "yes $firstCode"))

        then: "it waits on its own answer; the stale code was consumed and the approver told"
        !second.isDone()
        0 * gateway.onMessage(_)
        outbound.last().content().contains("No open approval request has code $firstCode")
    }

    def "with no approver configured the call is denied, not allowed"() {
        given: "a deployment that enabled chat approval but named nobody"
        wire([])

        when:
        def future = handler.requestApproval("rebootDevice", [:], "sess-1")

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
        def code = lastCode()
        Thread.sleep(25)

        when: "the approver replies after the window closed"
        filter.onMessage(replyFrom("9001", "yes $code"))

        then: "the stale answer does not authorise the call"
        !future.isDone()

        and: "and it is treated as an ordinary message — nothing was pending any more"
        1 * gateway.onMessage(_ as ChannelMessage)
    }

    def "the prompt states the timeout action when it is approve"() {
        given:
        def lenient = new ChatApprovalHandler(channels, registry,
                new ConfiguredApproverResolver([OWNER]),
                new ApprovalPolicy(false, Duration.ofMinutes(5),
                        ApprovalPolicy.OnTimeout.APPROVE, [:]))

        when:
        lenient.requestApproval("harmless", [:], "sess-1")

        then:
        outbound.last().content().contains("proceed automatically")
    }

    def "past the per-conversation bound a request is denied without evicting the others"() {
        given:
        def small = new PendingApprovalRegistry(java.time.Clock.systemUTC(), 2)
        def resolver = new ConfiguredApproverResolver([OWNER])
        def h = new ChatApprovalHandler(channels, small, resolver, ApprovalPolicy.DEFAULT)
        def a = h.requestApproval("t1", [:], "s")
        def b = h.requestApproval("t2", [:], "s")

        when:
        def c = h.requestApproval("t3", [:], "s")

        then:
        c.isDone()
        c.get() instanceof ToolApprovalDecision.Denied
        !a.isDone()
        !b.isDone()

        and: "no third prompt was sent — there was nothing to answer"
        outbound.size() == 2
    }
}
