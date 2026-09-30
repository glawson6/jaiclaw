package io.jaiclaw.core.agent;

import io.jaiclaw.core.api.Experimental;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-flight approval requests, keyed so an inbound chat reply can be matched
 * back to the tool call that is waiting on it.
 *
 * <p>An approval request and its answer arrive on different threads by
 * different routes: the agent's request thread parks on a future while the
 * human's reply comes in through a channel adapter. Something has to hold the
 * two halves together, and nothing in the framework did — the only prior
 * correlation store is MCP-specific and keyed by JSON-RPC id.
 *
 * <h2>Design notes</h2>
 *
 * <p><strong>Keyed by peer, not by session.</strong> A reply is a message from
 * a person in a conversation; the registry has to find the pending request from
 * nothing but "who said this, and where". The session key is recorded for
 * audit, not lookup.
 *
 * <p><strong>Single-use.</strong> {@link #redeem} removes before completing, so
 * a duplicate "yes" cannot approve a second tool call — including one queued
 * behind the first. Redemption is atomic under {@code ConcurrentHashMap}.
 *
 * <p><strong>Expiry is checked on access, not swept.</strong> This class stays
 * free of threads and of Spring so it can live in {@code jaiclaw-core} and be
 * used from a plain-Java embedding. An expired entry is invisible to
 * {@link #redeem} and is dropped when encountered; the waiting caller learns
 * of the timeout from its own bounded wait, which is where the timeout policy
 * already lives.
 *
 * <p><strong>Not persistent.</strong> Requests in flight are lost on restart.
 * That is the honest behaviour for a human-latency gate: on the other side of a
 * restart the agent run is gone too, so an approval arriving later has nothing
 * to authorise.
 */
@Experimental
public final class PendingApprovalRegistry {

    /**
     * One waiting approval request.
     *
     * @param approvalId  opaque id, unique per registry instance
     * @param toolName    tool awaiting approval
     * @param sessionKey  session the tool call belongs to (audit, not lookup)
     * @param peerKey     the conversation the question was asked in
     * @param askedAt     when the request was registered
     * @param expiresAt   when it stops being redeemable
     * @param future      completed by {@link #redeem}
     */
    public record PendingApproval(
            String approvalId,
            String toolName,
            String sessionKey,
            String peerKey,
            Instant askedAt,
            Instant expiresAt,
            CompletableFuture<ToolApprovalDecision> future
    ) {
        public boolean isExpiredAt(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    private final Map<String, PendingApproval> byPeer = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Clock clock;

    public PendingApprovalRegistry() {
        this(Clock.systemUTC());
    }

    /** Injectable clock, matching the convention in the audit and retention services. */
    public PendingApprovalRegistry(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * Builds the lookup key for a conversation.
     *
     * <p>Channel id is part of the key so the same numeric peer on two
     * platforms cannot answer for each other. Tenant id is included because in
     * multi-tenant deployments two tenants may legitimately carry the same
     * channel peer, and one must never redeem the other's approval.
     */
    public static String peerKey(String tenantId, String channelId, String peerId) {
        return (tenantId == null ? "-" : tenantId)
                + ":" + (channelId == null ? "-" : channelId)
                + ":" + (peerId == null ? "-" : peerId);
    }

    /**
     * Registers a request and returns the future the caller should wait on.
     *
     * <p>If that conversation already has a request outstanding, the existing
     * one is <strong>superseded</strong>: it is completed as denied so its
     * caller is not left parked, and the new request takes the slot. One
     * question at a time per conversation keeps a bare "yes" unambiguous — a
     * queue would make it impossible to know which call was approved.
     */
    public PendingApproval register(String toolName, String sessionKey, String peerKey,
                                    Duration window) {
        Instant now = clock.instant();
        Duration effective = (window == null || window.isZero() || window.isNegative())
                ? ApprovalPolicy.DEFAULT_TIMEOUT
                : window;
        PendingApproval created = new PendingApproval(
                "ap-" + sequence.incrementAndGet(),
                toolName,
                sessionKey,
                peerKey,
                now,
                now.plus(effective),
                new CompletableFuture<>());

        PendingApproval displaced = byPeer.put(peerKey, created);
        if (displaced != null && !displaced.future().isDone()) {
            displaced.future().complete(new ToolApprovalDecision.Denied(
                    "superseded by a newer approval request for '" + toolName + "'"));
        }
        return created;
    }

    /**
     * Completes the request outstanding for {@code peerKey} with
     * {@code decision}, if one is live.
     *
     * @return the redeemed request, or empty when there was nothing pending,
     *         it had already expired, or another thread redeemed it first
     */
    public Optional<PendingApproval> redeem(String peerKey, ToolApprovalDecision decision) {
        PendingApproval pending = byPeer.remove(peerKey);
        if (pending == null) {
            return Optional.empty();
        }
        if (pending.isExpiredAt(clock.instant())) {
            // Removed above, so it will not linger. The waiting caller has
            // already resolved via its own bounded wait.
            return Optional.empty();
        }
        boolean won = pending.future().complete(decision);
        return won ? Optional.of(pending) : Optional.empty();
    }

    /** True when {@code peerKey} has a live, unexpired request. */
    public boolean hasPending(String peerKey) {
        PendingApproval pending = byPeer.get(peerKey);
        if (pending == null) return false;
        if (pending.isExpiredAt(clock.instant())) {
            byPeer.remove(peerKey, pending);
            return false;
        }
        return true;
    }

    /** The live request for {@code peerKey}, if any. */
    public Optional<PendingApproval> peek(String peerKey) {
        return hasPending(peerKey) ? Optional.ofNullable(byPeer.get(peerKey)) : Optional.empty();
    }

    /**
     * Discards a request without completing its future — for the caller that
     * registered it and has since resolved by other means (its own timeout).
     * Leaving it registered would let a late reply redeem an approval for a
     * tool call that has already been decided.
     */
    public void discard(String approvalId) {
        if (approvalId == null) return;
        byPeer.values().removeIf(p -> approvalId.equals(p.approvalId()));
    }

    /**
     * Drops expired entries. Optional — expiry is enforced on access — but
     * useful for a deployment that wants the map bounded by a periodic sweep
     * rather than by traffic.
     *
     * @return how many entries were removed
     */
    public int purgeExpired() {
        Instant now = clock.instant();
        int removed = 0;
        for (Iterator<Map.Entry<String, PendingApproval>> it = byPeer.entrySet().iterator();
             it.hasNext(); ) {
            Map.Entry<String, PendingApproval> e = it.next();
            if (e.getValue().isExpiredAt(now)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Live entry count, expired-but-unswept included. Diagnostics only. */
    public int size() {
        return byPeer.size();
    }
}
