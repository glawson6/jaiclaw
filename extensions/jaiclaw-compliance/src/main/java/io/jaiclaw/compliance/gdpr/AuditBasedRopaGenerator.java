package io.jaiclaw.compliance.gdpr;

import io.jaiclaw.audit.AuditEvent;
import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.core.gdpr.RopaGenerator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * T3-1 reference {@link RopaGenerator} — reconstructs the RoPA from the
 * audit trail. Groups events by {@code action}, then aggregates the lawful
 * bases + data categories + recipients + retention values that appear on
 * each event in the group.
 *
 * <p>Assumes the AuditLogger's {@code query} contract returns events
 * most-recent-first; the impl filters by the requested window in memory.
 * Adopters with a large audit volume should override with a pushdown-query
 * impl.
 */
public class AuditBasedRopaGenerator implements RopaGenerator {

    private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);
    /** JaiClaw framework identifier used as the processor name. */
    public static final String PROCESSOR_NAME = "JaiClaw framework";

    private final List<AuditLogger> auditLoggers;
    private final Clock clock;

    public AuditBasedRopaGenerator(List<AuditLogger> auditLoggers) {
        this(auditLoggers, Clock.systemUTC());
    }

    public AuditBasedRopaGenerator(List<AuditLogger> auditLoggers, Clock clock) {
        this.auditLoggers = List.copyOf(auditLoggers == null ? List.of() : auditLoggers);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public Ropa generate(String tenantId, Instant from, Instant to) {
        if (tenantId == null || tenantId.isBlank())
            throw new IllegalArgumentException("tenantId must not be blank");
        Instant nowStamp = clock.instant();
        Instant effectiveTo = to == null ? nowStamp : to;
        Instant effectiveFrom = from == null ? effectiveTo.minus(DEFAULT_WINDOW) : from;

        // action → aggregator
        Map<String, ActivityAggregator> byAction = new LinkedHashMap<>();
        for (AuditLogger logger : auditLoggers) {
            List<AuditEvent> events = logger.query(tenantId, Integer.MAX_VALUE);
            for (AuditEvent e : events) {
                if (e.timestamp() == null) continue;
                if (e.timestamp().isBefore(effectiveFrom)) continue;
                if (!e.timestamp().isBefore(effectiveTo)) continue;
                byAction.computeIfAbsent(e.action(), k -> new ActivityAggregator(k)).add(e);
            }
        }

        List<ProcessingActivity> activities = new ArrayList<>();
        for (ActivityAggregator agg : byAction.values()) {
            activities.add(agg.toActivity());
        }

        return new Ropa(tenantId, effectiveFrom, effectiveTo, nowStamp,
                tenantId, PROCESSOR_NAME, activities);
    }

    /** Mutable roll-up scratchpad. */
    private static final class ActivityAggregator {
        final String action;
        long count = 0;
        final Set<String> lawfulBases = new TreeSet<>();
        final Set<String> dataCategories = new TreeSet<>();
        final Set<String> recipients = new TreeSet<>();
        final Map<String, Integer> retentionSummary = new HashMap<>();

        ActivityAggregator(String action) { this.action = action; }

        void add(AuditEvent e) {
            count++;
            if (e.lawfulBasis() != null && !e.lawfulBasis().isBlank()) lawfulBases.add(e.lawfulBasis());
            if (e.dataCategories() != null) dataCategories.addAll(e.dataCategories());
            if (e.recipients() != null) recipients.addAll(e.recipients());
            if (e.retentionDays() != null && e.lawfulBasis() != null) {
                retentionSummary.merge(e.lawfulBasis(), e.retentionDays(), Math::max);
            }
        }

        ProcessingActivity toActivity() {
            return new ProcessingActivity(action, count,
                    List.copyOf(lawfulBases), List.copyOf(dataCategories),
                    List.copyOf(recipients), Map.copyOf(retentionSummary));
        }
    }
}
