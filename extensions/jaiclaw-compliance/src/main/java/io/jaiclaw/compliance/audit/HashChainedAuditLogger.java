package io.jaiclaw.compliance.audit;

import io.jaiclaw.audit.AuditEvent;
import io.jaiclaw.audit.AuditLogger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * T2-6 — decorator that maintains an append-only cryptographic hash chain
 * over the wrapped {@link AuditLogger}. Each event's chain hash is
 * {@code SHA-256(previousHash || currentEventCanonical)}, stored under
 * {@code details["prevHash"]} + {@code details["chainHash"]}.
 *
 * <p>The chain is per-tenant — different tenants get independent chains
 * so a tenant-specific audit dump can be verified in isolation.
 *
 * <p>{@link #verifyChain(String)} replays every event for a tenant and
 * asserts the chain. Any break emits an {@code audit.integrity_violation}
 * event with the offending index — required by the T2-6 acceptance test.
 */
public class HashChainedAuditLogger implements AuditLogger {

    public static final String ACTION_INTEGRITY_VIOLATION = "audit.integrity_violation";
    private static final String KEY_PREV_HASH = "prevHash";
    private static final String KEY_CHAIN_HASH = "chainHash";
    private static final String GENESIS = "GENESIS";

    private final AuditLogger delegate;
    private final Clock clock;
    /** Last chain hash per tenantId. */
    private final Map<String, AtomicReference<String>> lastHashByTenant = new HashMap<>();

    public HashChainedAuditLogger(AuditLogger delegate) {
        this(delegate, Clock.systemUTC());
    }

    public HashChainedAuditLogger(AuditLogger delegate, Clock clock) {
        this.delegate = delegate;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public synchronized void log(AuditEvent event) {
        String tenantKey = event.tenantId() == null ? "" : event.tenantId();
        AtomicReference<String> last = lastHashByTenant.computeIfAbsent(tenantKey,
                k -> new AtomicReference<>(GENESIS));
        String prev = last.get();
        String canonical = canonical(event);
        String chainHash = sha256Base64(prev + "||" + canonical);

        Map<String, Object> details = new HashMap<>(event.details());
        details.put(KEY_PREV_HASH, prev);
        details.put(KEY_CHAIN_HASH, chainHash);

        AuditEvent stamped = AuditEvent.builder()
                .id(event.id()).timestamp(event.timestamp()).tenantId(event.tenantId())
                .actor(event.actor()).action(event.action()).resource(event.resource())
                .outcome(event.outcome()).details(details)
                .lawfulBasis(event.lawfulBasis()).dataCategories(event.dataCategories())
                .recipients(event.recipients()).retentionDays(event.retentionDays())
                .consentToken(event.consentToken()).build();

        delegate.log(stamped);
        last.set(chainHash);
    }

    @Override
    public List<AuditEvent> query(String tenantId, int limit) {
        return delegate.query(tenantId, limit);
    }

    @Override
    public Optional<AuditEvent> findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public long count(String tenantId) {
        return delegate.count(tenantId);
    }

    @Override
    public int purgeOlderThan(String tenantId, Instant cutoff) {
        return delegate.purgeOlderThan(tenantId, cutoff);
    }

    /**
     * Replay every stored event for {@code tenantId} in insertion order and
     * verify the chain. Emits an {@code audit.integrity_violation} event at
     * the first break; returns the report.
     *
     * <p>Assumes {@link AuditLogger#query} returns events most-recent-first —
     * the standard contract in the SPI's javadoc.
     */
    public IntegrityReport verifyChain(String tenantId) {
        List<AuditEvent> mostRecentFirst = delegate.query(tenantId, Integer.MAX_VALUE);
        // Reverse to insertion order.
        List<AuditEvent> ordered = new java.util.ArrayList<>(mostRecentFirst);
        java.util.Collections.reverse(ordered);

        String prev = GENESIS;
        for (int i = 0; i < ordered.size(); i++) {
            AuditEvent evt = ordered.get(i);
            Object storedPrev = evt.details().get(KEY_PREV_HASH);
            Object storedChain = evt.details().get(KEY_CHAIN_HASH);
            if (!(storedPrev instanceof String) || !(storedChain instanceof String)) {
                writeViolation(tenantId, i, evt.id(), "missing chain fields");
                return new IntegrityReport(false, i, evt.id(), "missing chain fields");
            }
            if (!prev.equals(storedPrev)) {
                writeViolation(tenantId, i, evt.id(), "prevHash mismatch");
                return new IntegrityReport(false, i, evt.id(), "prevHash mismatch");
            }
            String expected = sha256Base64(prev + "||" + canonical(evt, /*excludeChain*/ true));
            if (!expected.equals(storedChain)) {
                writeViolation(tenantId, i, evt.id(), "chainHash recomputation mismatch");
                return new IntegrityReport(false, i, evt.id(), "chainHash recomputation mismatch");
            }
            prev = (String) storedChain;
        }
        return new IntegrityReport(true, -1, null, null);
    }

    private void writeViolation(String tenantId, int index, String offendingEventId, String reason) {
        AuditEvent violation = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(clock.instant())
                .tenantId(tenantId)
                .actor("system")
                .action(ACTION_INTEGRITY_VIOLATION)
                .resource(offendingEventId == null ? "" : offendingEventId)
                .outcome(AuditEvent.Outcome.FAILURE)
                .details(Map.of("index", index, "reason", reason))
                .build();
        try {
            delegate.log(violation);
        } catch (RuntimeException ignored) {
            // Reporting a violation must not itself throw.
        }
    }

    private String canonical(AuditEvent e) {
        return canonical(e, false);
    }

    /**
     * Canonicalize an event for hashing. When {@code excludeChain} is true, the
     * {@code prevHash} + {@code chainHash} entries in {@code details} are
     * ignored — that's what verification needs to recompute the expected
     * chain hash without including the stored hash in the input.
     */
    private String canonical(AuditEvent e, boolean excludeChain) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.id()).append('|')
          .append(e.timestamp()).append('|')
          .append(e.tenantId() == null ? "" : e.tenantId()).append('|')
          .append(e.actor()).append('|')
          .append(e.action()).append('|')
          .append(e.resource()).append('|')
          .append(e.outcome());
        // Sort details by key for stable order.
        java.util.TreeMap<String, Object> sorted = new java.util.TreeMap<>(e.details());
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (excludeChain && (KEY_PREV_HASH.equals(entry.getKey()) || KEY_CHAIN_HASH.equals(entry.getKey()))) continue;
            sb.append('|').append(entry.getKey()).append('=').append(String.valueOf(entry.getValue()));
        }
        return sb.toString();
    }

    private static String sha256Base64(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Result of a chain-integrity verification pass.
     *
     * @param valid           true when the chain replayed cleanly
     * @param brokenAt        index (in insertion order) of the first offending
     *                        event, or -1 if the chain is valid
     * @param offendingEventId id of the offending event, or null
     * @param reason           human-readable reason, or null
     */
    public record IntegrityReport(boolean valid, int brokenAt, String offendingEventId, String reason) {}
}
