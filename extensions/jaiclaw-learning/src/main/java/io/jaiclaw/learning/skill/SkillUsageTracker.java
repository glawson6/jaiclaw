package io.jaiclaw.learning.skill;

import io.jaiclaw.core.api.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records that a learned skill was included in a prompt, so the curator ages
 * skills on actual use rather than on age alone.
 *
 * <p>Without this, {@code lastUsedAt} is never written and
 * {@link io.jaiclaw.learning.curator.SkillCurator} falls back to
 * {@code createdAt} — meaning a skill used every single day would still be
 * archived 90 days after it was written. That is the bug this class fixes, not
 * merely a statistic it adds.
 *
 * <p><strong>Never writes on the hot path.</strong> Prompt building happens on
 * every turn; a synchronous sidecar write there would put filesystem latency in
 * front of the user. Instead hits are accumulated in memory and flushed on a
 * cadence, or when {@link #flush()} is called explicitly. Losing a few counts to
 * a crash is acceptable — this feeds a 30-day ageing heuristic, not billing.
 *
 * <p>Phase 4B of the 1.2.0 plan.
 */
@Experimental
public class SkillUsageTracker {

    private static final Logger log = LoggerFactory.getLogger(SkillUsageTracker.class);

    /** Minimum gap between flushes of the same skill, so a chatty session writes once. */
    private static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofMinutes(5);

    private final SkillWriter skills;
    private final Clock clock;
    private final Duration flushInterval;

    /** Pending hit counts, keyed {@code tenantId + '\0' + skillName}. */
    private final Map<String, Integer> pending = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastFlush = new ConcurrentHashMap<>();

    public SkillUsageTracker(SkillWriter skills) {
        this(skills, Clock.systemUTC(), DEFAULT_FLUSH_INTERVAL);
    }

    public SkillUsageTracker(SkillWriter skills, Clock clock, Duration flushInterval) {
        this.skills = skills;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.flushInterval = flushInterval == null ? DEFAULT_FLUSH_INTERVAL : flushInterval;
    }

    /**
     * Records that these skills were included in a prompt. Returns immediately;
     * the sidecar write happens on the flush cadence.
     *
     * @param skillNames names included this turn; unknown names are harmless
     */
    public void recordUse(String tenantId, Set<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) return;
        for (String name : skillNames) {
            if (name == null || name.isBlank()) continue;
            pending.merge(key(tenantId, name), 1, Integer::sum);
        }
        flushDue(tenantId);
    }

    /** Convenience for a single skill. */
    public void recordUse(String tenantId, String skillName) {
        recordUse(tenantId, Set.of(skillName));
    }

    /**
     * Writes every pending count to its sidecar, regardless of cadence. Called on
     * shutdown, and by tests.
     *
     * @return how many sidecars were updated
     */
    public int flush() {
        int written = 0;
        for (Map.Entry<String, Integer> entry : Map.copyOf(pending).entrySet()) {
            String[] parts = entry.getKey().split("\0", 2);
            if (parts.length != 2) {
                pending.remove(entry.getKey());
                continue;
            }
            if (writeOne(parts[0], parts[1], entry.getValue())) written++;
            pending.remove(entry.getKey());
        }
        return written;
    }

    /** Pending, unflushed hits. Diagnostics and tests. */
    public int pendingCount() {
        return pending.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Flushes only the entries for this tenant whose cadence window has elapsed. */
    private void flushDue(String tenantId) {
        Instant now = clock.instant();
        for (Map.Entry<String, Integer> entry : Map.copyOf(pending).entrySet()) {
            String[] parts = entry.getKey().split("\0", 2);
            if (parts.length != 2 || !parts[0].equals(nullSafe(tenantId))) continue;

            Instant previous = lastFlush.get(entry.getKey());
            if (previous != null && Duration.between(previous, now).compareTo(flushInterval) < 0) {
                continue;
            }
            writeOne(parts[0], parts[1], entry.getValue());
            pending.remove(entry.getKey());
            lastFlush.put(entry.getKey(), now);
        }
    }

    /**
     * Applies {@code hits} to one skill's sidecar.
     *
     * <p>A skill with no sidecar is skipped rather than given one: sidecars are
     * created by the learning module when it writes a skill, and inventing one
     * here would claim a hand-authored skill was agent-authored.
     */
    private boolean writeOne(String tenantId, String skillName, int hits) {
        try {
            LearnedSkillSidecar sidecar = skills.readSidecar(tenantId, skillName).orElse(null);
            if (sidecar == null) return false;

            LearnedSkillSidecar updated = sidecar;
            for (int i = 0; i < hits; i++) {
                updated = updated.withUse(clock.instant());
            }
            skills.writeSidecar(tenantId, skillName, updated);
            return true;
        } catch (RuntimeException e) {
            // Usage tracking must never break a turn or a shutdown.
            log.debug("Could not record usage for skill {} (tenant {})", skillName, tenantId, e);
            return false;
        }
    }

    private static String key(String tenantId, String skillName) {
        return nullSafe(tenantId) + "\0" + skillName;
    }

    private static String nullSafe(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }
}
