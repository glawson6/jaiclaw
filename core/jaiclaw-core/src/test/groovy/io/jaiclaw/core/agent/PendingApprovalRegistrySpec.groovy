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
        PendingApprovalRegistry.peerKey("acme", "telegram", peer)
    }

    def "a registered request is pending and redeemable"() {
        given:
        def pending = registry.register("shell_exec", "sess-1", key(), Duration.ofMinutes(5))

        expect:
        registry.hasPending(key())
        !pending.future().isDone()
        pending.toolName() == "shell_exec"
        pending.sessionKey() == "sess-1"

        when:
        def redeemed = registry.redeem(key(), new ToolApprovalDecision.Approved())

        then:
        redeemed.isPresent()
        pending.future().get() instanceof ToolApprovalDecision.Approved

        and: "and it is gone — no longer pending"
        !registry.hasPending(key())
    }

    def "redemption is single-use, so one yes cannot approve two calls"() {
        given:
        def pending = registry.register("shell_exec", "sess-1", key(), Duration.ofMinutes(5))

        when:
        def first = registry.redeem(key(), new ToolApprovalDecision.Approved())
        def second = registry.redeem(key(), new ToolApprovalDecision.Approved())

        then:
        first.isPresent()
        second.isEmpty()
        pending.future().get() instanceof ToolApprovalDecision.Approved
    }

    def "redeem REMOVES the entry rather than leaving it replayable"() {
        given: "two mechanisms protect single-use; this pins the removal specifically,"
        and: "because relying only on future-completion leaves the entry addressable"
        registry.register("shell_exec", "sess-1", key(), Duration.ofMinutes(5))

        when:
        registry.redeem(key(), new ToolApprovalDecision.Approved())

        then: "the slot is free, so a queued call cannot inherit the answer"
        registry.size() == 0
        registry.peek(key()).isEmpty()
        !registry.hasPending(key())
    }

    def "a redeemed slot does not answer a subsequent request for the same peer"() {
        given: "the replay scenario that matters: approve once, then a new tool asks"
        def first = registry.register("shell_exec", "sess-1", key(), Duration.ofMinutes(5))
        registry.redeem(key(), new ToolApprovalDecision.Approved())

        when: "a second, different tool call asks the same conversation"
        def second = registry.register("dropDatabase", "sess-1", key(), Duration.ofMinutes(5))

        then: "it waits on its own answer — it did not inherit the earlier yes"
        !second.future().isDone()
        first.future().get() instanceof ToolApprovalDecision.Approved
    }

    def "redeeming with nothing pending is a no-op, not an error"() {
        expect:
        registry.redeem(key(), new ToolApprovalDecision.Approved()).isEmpty()
        registry.redeem(key("unknown"), new ToolApprovalDecision.Denied("x")).isEmpty()
    }

    def "an expired request is not redeemable"() {
        given:
        registry.register("shell_exec", "sess-1", key(), Duration.ofMinutes(2))

        when: "the window closes before anyone replies"
        clock.advance(Duration.ofMinutes(3))

        then:
        !registry.hasPending(key())

        and: "a late yes cannot authorise the call"
        registry.redeem(key(), new ToolApprovalDecision.Approved()).isEmpty()
    }

    def "expiry is exact at the boundary"() {
        given:
        registry.register("t", "s", key(), Duration.ofMinutes(5))

        when: "one tick short of the deadline"
        clock.advance(Duration.ofMinutes(5).minusMillis(1))

        then:
        registry.hasPending(key())

        when: "exactly at the deadline"
        clock.advance(Duration.ofMillis(1))

        then: "expired — the window is inclusive of its end"
        !registry.hasPending(key())
    }

    def "peers are isolated — one conversation cannot answer for another"() {
        given:
        def a = registry.register("shell_exec", "sess-a", key("chat-a"), Duration.ofMinutes(5))
        def b = registry.register("file_write", "sess-b", key("chat-b"), Duration.ofMinutes(5))

        when: "chat-a approves"
        registry.redeem(key("chat-a"), new ToolApprovalDecision.Approved())

        then: "only chat-a's request resolves"
        a.future().isDone()
        !b.future().isDone()
        registry.hasPending(key("chat-b"))
    }

    def "tenants are isolated even with the same channel peer"() {
        given: "two tenants legitimately sharing a peer id"
        def acme = PendingApprovalRegistry.peerKey("acme", "telegram", "555")
        def beta = PendingApprovalRegistry.peerKey("beta", "telegram", "555")
        def acmePending = registry.register("shell_exec", "s-acme", acme, Duration.ofMinutes(5))
        def betaPending = registry.register("shell_exec", "s-beta", beta, Duration.ofMinutes(5))

        when:
        registry.redeem(acme, new ToolApprovalDecision.Approved())

        then: "beta's approval is untouched"
        acmePending.future().isDone()
        !betaPending.future().isDone()
    }

    def "channels are isolated — same numeric peer on two platforms"() {
        given:
        def tg = PendingApprovalRegistry.peerKey("acme", "telegram", "555")
        def slack = PendingApprovalRegistry.peerKey("acme", "slack", "555")
        def tgPending = registry.register("t", "s", tg, Duration.ofMinutes(5))
        def slackPending = registry.register("t", "s", slack, Duration.ofMinutes(5))

        when:
        registry.redeem(tg, new ToolApprovalDecision.Approved())

        then:
        tgPending.future().isDone()
        !slackPending.future().isDone()
    }

    def "a second request for the same peer supersedes the first"() {
        given: "one question at a time per conversation keeps a bare yes unambiguous"
        def first = registry.register("shell_exec", "sess-1", key(), Duration.ofMinutes(5))

        when:
        def second = registry.register("file_write", "sess-1", key(), Duration.ofMinutes(5))

        then: "the displaced caller is not left parked forever"
        first.future().isDone()
        first.future().get() instanceof ToolApprovalDecision.Denied
        ((ToolApprovalDecision.Denied) first.future().get()).reason().contains("superseded")

        and: "the newer request holds the slot"
        !second.future().isDone()
        registry.peek(key()).get().toolName() == "file_write"
    }

    def "discard removes a request without completing it"() {
        given: "the caller has already resolved via its own bounded wait"
        def pending = registry.register("shell_exec", "sess-1", key(), Duration.ofMinutes(5))

        when:
        registry.discard(pending.approvalId())

        then: "a late reply has nothing to redeem"
        !registry.hasPending(key())
        registry.redeem(key(), new ToolApprovalDecision.Approved()).isEmpty()

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
        def zero = registry.register("t", "s", key("a"), Duration.ZERO)
        def negative = registry.register("t", "s", key("b"), Duration.ofMinutes(-5))
        def nullWindow = registry.register("t", "s", key("c"), null)

        then: "otherwise the request would expire the instant it was asked"
        [zero, negative, nullWindow].every {
            it.expiresAt() == clock.instant().plus(ApprovalPolicy.DEFAULT_TIMEOUT)
        }
    }

    def "purgeExpired drops only expired entries"() {
        given:
        registry.register("t", "s", key("short"), Duration.ofMinutes(1))
        registry.register("t", "s", key("long"), Duration.ofMinutes(30))

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
        PendingApprovalRegistry.peerKey(null, "telegram", "1") !=
                PendingApprovalRegistry.peerKey("acme", "telegram", "1")
        PendingApprovalRegistry.peerKey("acme", null, "1") !=
                PendingApprovalRegistry.peerKey("acme", "telegram", "1")
    }

    def "approval ids are unique"() {
        when:
        def ids = (1..50).collect {
            registry.register("t", "s", key("peer-$it"), Duration.ofMinutes(5)).approvalId()
        }

        then:
        ids.toSet().size() == 50
    }
}
