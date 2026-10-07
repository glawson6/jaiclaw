package io.jaiclaw.core.agent

import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class PendingApprovalRegistrySpec extends Specification {

    /** Hand-cranked clock, so expiry is tested without sleeping. */
    static class TickingClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z")

        @Override Instant instant() { now }
        @Override ZoneOffset getZone() { ZoneOffset.UTC }
        @Override Clock withZone(java.time.ZoneId zone) { this }

        void advance(Duration d) { now = now.plus(d) }
    }

    TickingClock clock = new TickingClock()
    PendingApprovalRegistry registry = new PendingApprovalRegistry(clock)

    private static String key(String peer = "chat-1") {
        PendingApprovalRegistry.peerKey("telegram", peer)
    }

    private PendingApprovalRegistry.PendingApproval ask(String tool = "shell_exec", String peer = "chat-1",
                                                        Duration window = Duration.ofMinutes(5)) {
        registry.register(tool, "sess-1", "acme", key(peer), window)
    }

    def "a registered request is pending and redeemable by its code"() {
        given:
        def pending = ask()

        expect:
        registry.hasPending(key())
        !pending.future().isDone()
        pending.toolName() == "shell_exec"
        pending.sessionKey() == "sess-1"
        pending.tenantId() == "acme"
        PendingApprovalRegistry.isCode(pending.approvalId())

        when:
        def redeemed = registry.redeem(key(), pending.approvalId(), new ToolApprovalDecision.Approved())

        then:
        redeemed.isPresent()
        pending.future().get() instanceof ToolApprovalDecision.Approved

        and: "and it is gone — no longer pending"
        !registry.hasPending(key())
    }

    def "the code is accepted case-insensitively — approvers type on phones"() {
        given:
        def pending = ask()

        when:
        def redeemed = registry.redeem(key(), pending.approvalId().toLowerCase(),
                new ToolApprovalDecision.Approved())

        then:
        redeemed.isPresent()
    }

    def "redemption is single-use, so one yes cannot approve two calls"() {
        given:
        def pending = ask()

        when:
        def first = registry.redeem(key(), pending.approvalId(), new ToolApprovalDecision.Approved())
        def second = registry.redeem(key(), pending.approvalId(), new ToolApprovalDecision.Approved())

        then:
        first.isPresent()
        second.isEmpty()
        pending.future().get() instanceof ToolApprovalDecision.Approved
    }

    def "redeem REMOVES the entry rather than leaving it replayable"() {
        given:
        def pending = ask()

        when:
        registry.redeem(key(), pending.approvalId(), new ToolApprovalDecision.Approved())

        then: "the code is dead, so a queued call cannot inherit the answer"
        registry.size() == 0
        registry.find(pending.approvalId()).isEmpty()
        !registry.hasPending(key())
    }

    def "a code is bound to the conversation it was asked in"() {
        given: "a question asked in chat-a"
        def pending = ask("shell_exec", "chat-a")

        when: "someone in chat-b learns the code and replies with it"
        def redeemed = registry.redeem(key("chat-b"), pending.approvalId(),
                new ToolApprovalDecision.Approved())

        then: "nothing happens — and the request is still live for chat-a"
        redeemed.isEmpty()
        !pending.future().isDone()
        registry.hasPending(key("chat-a"))
    }

    def "two outstanding requests in one conversation are answered independently"() {
        given: "the confirmation-hijack scenario: a benign call, then a dangerous one"
        def wiki = ask("wiki_read")
        def shell = ask("shell_exec")

        expect: "both are open, nothing was superseded"
        !wiki.future().isDone()
        !shell.future().isDone()
        registry.pendingFor(key())*.toolName() == ["wiki_read", "shell_exec"]

        when: "the approver answers the one they were looking at"
        registry.redeem(key(), wiki.approvalId(), new ToolApprovalDecision.Approved())

        then: "only that one resolves"
        wiki.future().get() instanceof ToolApprovalDecision.Approved
        !shell.future().isDone()
        registry.pendingFor(key())*.toolName() == ["shell_exec"]
    }

    def "a new request never evicts an older one — no remote cancellation primitive"() {
        given:
        def first = ask("shell_exec")

        when: "many more requests arrive for the same conversation"
        (1..10).each { ask("tool-$it") }

        then: "the first is untouched"
        !first.future().isDone()
        registry.hasPending(key())
        registry.find(first.approvalId()).isPresent()
    }

    def "past the per-conversation bound a new request is denied, not queued and not evicting"() {
        given:
        def bounded = new PendingApprovalRegistry(clock, 2)
        def a = bounded.register("t1", "s", null, key(), Duration.ofMinutes(5))
        def b = bounded.register("t2", "s", null, key(), Duration.ofMinutes(5))

        when:
        def c = bounded.register("t3", "s", null, key(), Duration.ofMinutes(5))

        then: "the third is already decided — denied — and was not stored"
        c.future().isDone()
        c.future().get() instanceof ToolApprovalDecision.Denied
        ((ToolApprovalDecision.Denied) c.future().get()).reason().contains("too many")
        bounded.size() == 2

        and: "the earlier two are still waiting on their own answers"
        !a.future().isDone()
        !b.future().isDone()

        and: "another conversation is unaffected by this one's bound"
        !bounded.register("t4", "s", null, key("other"), Duration.ofMinutes(5)).future().isDone()
    }

    def "redeeming an unknown code is a no-op, not an error"() {
        given:
        ask()

        expect:
        registry.redeem(key(), "ZZZZ", new ToolApprovalDecision.Approved()).isEmpty()
        registry.redeem(key(), null, new ToolApprovalDecision.Approved()).isEmpty()
        registry.redeem(key(), "", new ToolApprovalDecision.Denied("x")).isEmpty()
    }

    def "an expired request is not redeemable"() {
        given:
        def pending = ask("shell_exec", "chat-1", Duration.ofMinutes(2))

        when: "the window closes before anyone replies"
        clock.advance(Duration.ofMinutes(3))

        then:
        !registry.hasPending(key())

        and: "a late yes cannot authorise the call"
        registry.redeem(key(), pending.approvalId(), new ToolApprovalDecision.Approved()).isEmpty()
    }

    def "expiry is exact at the boundary"() {
        given:
        ask("t", "chat-1", Duration.ofMinutes(5))

        when: "one tick short of the deadline"
        clock.advance(Duration.ofMinutes(5).minusMillis(1))

        then:
        registry.hasPending(key())

        when: "exactly at the deadline"
        clock.advance(Duration.ofMillis(1))

        then: "expired — the window is inclusive of its end"
        !registry.hasPending(key())
    }

    def "channels are isolated — same numeric peer on two platforms"() {
        given:
        def tg = registry.register("t", "s", null,
                PendingApprovalRegistry.peerKey("telegram", "555"), Duration.ofMinutes(5))
        def slack = registry.register("t", "s", null,
                PendingApprovalRegistry.peerKey("slack", "555"), Duration.ofMinutes(5))

        when: "a Slack reply quotes the Telegram code"
        def redeemed = registry.redeem(PendingApprovalRegistry.peerKey("slack", "555"),
                tg.approvalId(), new ToolApprovalDecision.Approved())

        then:
        redeemed.isEmpty()
        !tg.future().isDone()
        !slack.future().isDone()
    }

    def "the conversation key carries no tenant, so both halves of the round trip agree"() {
        given: "the handler registers with tenant context, the filter redeems without it"
        def asked = registry.register("shell_exec", "sess", "acme", key(), Duration.ofMinutes(5))

        when:
        def redeemed = registry.redeem(key(), asked.approvalId(), new ToolApprovalDecision.Approved())

        then:
        redeemed.isPresent()

        and: "the tenant is still available on the record for audit"
        redeemed.get().tenantId() == "acme"
    }

    def "discard removes a request without completing it"() {
        given: "the caller has already resolved via its own bounded wait"
        def pending = ask()

        when:
        registry.discard(pending.approvalId())

        then: "a late reply has nothing to redeem"
        !registry.hasPending(key())
        registry.redeem(key(), pending.approvalId(), new ToolApprovalDecision.Approved()).isEmpty()

        and: "the future is left alone — the caller owns its own resolution"
        !pending.future().isDone()
    }

    def "discard tolerates an unknown or null id"() {
        when:
        registry.discard("nope")
        registry.discard(null)

        then:
        noExceptionThrown()
    }

    def "a non-positive window falls back to the policy default"() {
        when:
        def zero = ask("t", "a", Duration.ZERO)
        def negative = ask("t", "b", Duration.ofMinutes(-5))
        def nullWindow = ask("t", "c", null)

        then: "otherwise the request would expire the instant it was asked"
        [zero, negative, nullWindow].every {
            it.expiresAt() == clock.instant().plus(ApprovalPolicy.DEFAULT_TIMEOUT)
        }
    }

    def "purgeExpired drops only expired entries"() {
        given:
        ask("t", "short", Duration.ofMinutes(1))
        ask("t", "long", Duration.ofMinutes(30))

        when:
        clock.advance(Duration.ofMinutes(5))
        int removed = registry.purgeExpired()

        then:
        removed == 1
        registry.size() == 1
        registry.hasPending(key("long"))
    }

    def "peerKey tolerates nulls without colliding"() {
        expect:
        PendingApprovalRegistry.peerKey(null, "1") != PendingApprovalRegistry.peerKey("telegram", "1")
        PendingApprovalRegistry.peerKey("telegram", null) != PendingApprovalRegistry.peerKey("telegram", "1")
    }

    def "codes are unique among live requests"() {
        when:
        def ids = (1..200).collect { ask("t", "peer-$it").approvalId() }

        then:
        ids.toSet().size() == 200
    }

    def "codes can never spell a verdict word"() {
        expect: "no vowels and no Y in the alphabet, so no code equals an approve/deny token"
        !PendingApprovalRegistry.CODE_ALPHABET.any { "AEIOUY".contains(it) }
        !PendingApprovalRegistry.CODE_ALPHABET.any { "0O1IL".contains(it) }
        (ApprovalReplyParser.approveTokens() + ApprovalReplyParser.denyTokens()).every {
            !PendingApprovalRegistry.isCode(it)
        }
    }

    def "isCode recognises the shape and nothing else"() {
        expect:
        PendingApprovalRegistry.isCode("K7Q4")
        PendingApprovalRegistry.isCode("k7q4")
        !PendingApprovalRegistry.isCode("K7Q")
        !PendingApprovalRegistry.isCode("K7Q45")
        !PendingApprovalRegistry.isCode("yes")
        !PendingApprovalRegistry.isCode("DENY")
        !PendingApprovalRegistry.isCode("1O0I")
        !PendingApprovalRegistry.isCode(null)
    }
}
