package io.jaiclaw.core.agent;

import io.jaiclaw.core.api.Experimental;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-flight approval requests, keyed so an inbound chat reply can be matched
 * back to the <em>exact</em> tool call that is waiting on it.
 *
 * <p>An approval request and its answer arrive on different threads by
 * different routes: the agent's request thread parks on a future while the
 * human's reply comes in through a channel adapter. Something has to hold the
 * two halves together, and nothing in the framework did — the only prior
 * correlation store is MCP-specific and keyed by JSON-RPC id.
 *
 * <h2>Design notes</h2>
 *
 * <p><strong>Keyed by code, scoped by conversation.</strong> Every request is
 * issued a short human-typable code ({@value #CODE_LENGTH} characters from an
 * alphabet with no vowels and no look-alike glyphs, so it can never spell a
 * verdict word and is unambiguous when read off a phone). The approver's reply
 * must carry that code, and {@link #redeem} additionally requires the reply to
 * come from the conversation the question was asked in. This binds the answer
 * to the question: a "yes" meant for a harmless call cannot be applied to a
 * dangerous one that arrived a moment later — the confirmation-hijack the
 * 2026-10-01 security review demonstrated against the earlier design, which
 * kept one slot per conversation and let a newer request <em>supersede</em> the
 * one the approver was looking at.
 *
 * <p><strong>Several requests may be outstanding per conversation.</strong>
 * Because each has its own code there is no ambiguity, so nothing needs to be
 * evicted to make room. That also removes the remote denial-of-service the
 * supersede rule created: anyone who could induce a gated tool call could
 * previously cancel whatever approval was pending. The count per conversation
 * is bounded ({@link #DEFAULT_MAX_PENDING_PER_PEER}); past the bound a new
 * request is denied immediately rather than displacing an older one.
 *
 * <p><strong>The conversation key carries no tenant.</strong> An approver is a
 * deployment-wide identity configured by the operator, and the reply arrives
 * through a gateway filter that runs <em>before</em> tenant resolution. Putting
 * the tenant in the key made the two halves compute different keys in
 * multi-tenant mode, so no reply could ever match. The tenant of the
 * <em>requesting</em> session is recorded on the {@link PendingApproval} for
 * audit instead.
 *
 * <p><strong>Single-use.</strong> {@link #redeem} removes before completing, so
 * a duplicate "yes" cannot approve a second tool call. Redemption is atomic
 * under {@code ConcurrentHashMap}.
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

    /** Upper bound on live requests per conversation before new ones are denied. */
    public static final int DEFAULT_MAX_PENDING_PER_PEER = 20;

    /** Length of the code an approver must echo back. */
    public static final int CODE_LENGTH = 4;

    /**
     * Code alphabet: consonants and digits only, minus look-alikes.
     *
     * <p>No vowels and no {@code Y}, so a code can never read as a word —
     * in particular it can never equal an approve/deny token. No {@code 0 O 1 I L}
     * because they are indistinguishable in many chat fonts.
     */
    static final String CODE_ALPHABET = "BCDFGHJKMNPQRSTVWXZ23456789";

    /**
     * One waiting approval request.
     *
     * @param approvalId  the code the approver must echo back; unique among live requests
     * @param toolName    tool awaiting approval
     * @param sessionKey  session the tool call belongs to (audit, not lookup)
     * @param tenantId    tenant of the requesting session, or {@code null} in single-tenant mode (audit)
     * @param peerKey     the conversation the question was asked in — see {@link #peerKey}
     * @param askedAt     when the request was registered
     * @param expiresAt   when it stops being redeemable
     * @param sequence    registration order, for a stable listing when two requests share an instant
     * @param future      completed by {@link #redeem}
     */
    public record PendingApproval(
            String approvalId,
            String toolName,
            String sessionKey,
            String tenantId,
            String peerKey,
            Instant askedAt,
            Instant expiresAt,
            long sequence,
            CompletableFuture<ToolApprovalDecision> future
    ) {
        public boolean isExpiredAt(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    private final Map<String, PendingApproval> byCode = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final AtomicLong sequence = new AtomicLong();
    private final Clock clock;
    private final int maxPendingPerPeer;

    public PendingApprovalRegistry() {
        this(Clock.systemUTC(), DEFAULT_MAX_PENDING_PER_PEER);
    }

    /** Injectable clock, matching the convention in the audit and retention services. */
    public PendingApprovalRegistry(Clock clock) {
        this(clock, DEFAULT_MAX_PENDING_PER_PEER);
    }

    public PendingApprovalRegistry(Clock clock, int maxPendingPerPeer) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.maxPendingPerPeer = maxPendingPerPeer > 0 ? maxPendingPerPeer : DEFAULT_MAX_PENDING_PER_PEER;
    }

    /**
     * Builds the key for a conversation.
     *
     * <p>Channel id is part of the key so the same numeric peer on two
     * platforms cannot answer for each other. The tenant is deliberately
     * <em>not</em> part of it — see the class notes.
     */
    public static String peerKey(String channelId, String peerId) {
        return (channelId == null ? "-" : channelId)
                + ":" + (peerId == null ? "-" : peerId);
    }

    /** True when {@code text} has the shape of an approval code (case-insensitive). */
    public static boolean isCode(String text) {
        if (text == null || text.length() != CODE_LENGTH) {
            return false;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            if (CODE_ALPHABET.indexOf(upper.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Canonical form of a code as typed by a human. */
    public static String normalizeCode(String code) {
        return code == null ? null : code.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * Registers a request and returns the future the caller should wait on.
     *
     * <p>Other requests already outstanding for the same conversation are left
     * untouched — each carries its own code, so there is nothing to disambiguate
     * and nothing to evict. When the conversation already has
     * {@code maxPendingPerPeer} live requests, the returned entry is
     * <strong>already denied</strong> and is not stored; the caller sees an
     * ordinary denial rather than an exception.
     *
     * @param tenantId tenant of the requesting session; may be {@code null}
     */
    public PendingApproval register(String toolName, String sessionKey, String tenantId,
                                    String peerKey, Duration window) {
        Instant now = clock.instant();
        Duration effective = (window == null || window.isZero() || window.isNegative())
                ? ApprovalPolicy.DEFAULT_TIMEOUT
                : window;

        if (pendingFor(peerKey).size() >= maxPendingPerPeer) {
            CompletableFuture<ToolApprovalDecision> denied = CompletableFuture.completedFuture(
                    new ToolApprovalDecision.Denied("too many approval requests are already "
                            + "outstanding for this conversation (" + maxPendingPerPeer + ")"));
            return new PendingApproval("-", toolName, sessionKey, tenantId, peerKey,
                    now, now, sequence.incrementAndGet(), denied);
        }

        while (true) {
            String code = newCode();
            PendingApproval created = new PendingApproval(
                    code, toolName, sessionKey, tenantId, peerKey,
                    now, now.plus(effective), sequence.incrementAndGet(), new CompletableFuture<>());
            if (byCode.putIfAbsent(code, created) == null) {
                return created;
            }
            // Collision with a live code — draw again. 27^4 ≈ 531k codes
            // against a map bounded per conversation; this loop is short.
        }
    }

    /**
     * Completes the request with {@code code} using {@code decision}, provided
     * the reply comes from the conversation the question was asked in.
     *
     * @return the redeemed request, or empty when the code is unknown, belongs
     *         to another conversation, had already expired, or another thread
     *         redeemed it first
     */
    public Optional<PendingApproval> redeem(String peerKey, String code, ToolApprovalDecision decision) {
        String key = normalizeCode(code);
        if (key == null || decision == null) {
            return Optional.empty();
        }
        PendingApproval pending = byCode.get(key);
        if (pending == null) {
            return Optional.empty();
        }
        if (!pending.peerKey().equals(peerKey)) {
            // A code from one conversation cannot be redeemed from another —
            // and the entry stays live for its rightful approver.
            return Optional.empty();
        }
        if (pending.isExpiredAt(clock.instant())) {
            byCode.remove(key, pending);
            return Optional.empty();
        }
        if (!byCode.remove(key, pending)) {
            return Optional.empty();
        }
        boolean won = pending.future().complete(decision);
        return won ? Optional.of(pending) : Optional.empty();
    }

    /** True when {@code peerKey} has at least one live, unexpired request. */
    public boolean hasPending(String peerKey) {
        return !pendingFor(peerKey).isEmpty();
    }

    /** Live requests for {@code peerKey}, oldest first. Expired entries met on the way are dropped. */
    public List<PendingApproval> pendingFor(String peerKey) {
        Instant now = clock.instant();
        return byCode.values().stream()
                .filter(p -> p.peerKey().equals(peerKey))
                .filter(p -> {
                    if (p.isExpiredAt(now)) {
                        byCode.remove(p.approvalId(), p);
                        return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparing(PendingApproval::askedAt)
                        .thenComparingLong(PendingApproval::sequence))
                .toList();
    }

    /** The live request with this code, if any. */
    public Optional<PendingApproval> find(String code) {
        String key = normalizeCode(code);
        if (key == null) return Optional.empty();
        PendingApproval pending = byCode.get(key);
        if (pending == null) return Optional.empty();
        if (pending.isExpiredAt(clock.instant())) {
            byCode.remove(key, pending);
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    /**
     * Discards a request without completing its future — for the caller that
     * registered it and has since resolved by other means (its own timeout).
     * Leaving it registered would let a late reply redeem an approval for a
     * tool call that has already been decided.
     */
    public void discard(String approvalId) {
        if (approvalId == null) return;
        byCode.remove(normalizeCode(approvalId));
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
        for (Iterator<Map.Entry<String, PendingApproval>> it = byCode.entrySet().iterator();
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
        return byCode.size();
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
