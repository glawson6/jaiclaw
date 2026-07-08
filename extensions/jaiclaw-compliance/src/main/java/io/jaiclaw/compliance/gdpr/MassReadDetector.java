package io.jaiclaw.compliance.gdpr;

import io.jaiclaw.audit.AuditEvent;
import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.core.gdpr.AnomalyDetector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * T3-3 reference {@link AnomalyDetector} — flags actors whose distinct
 * subject-read count in the window exceeds a threshold. Catches "an admin is
 * scraping the database" patterns.
 *
 * <p>Read events are identified by their action starting with any of the
 * configured {@code readActionPrefixes} ({@code "data.read"},
 * {@code "session.load"} by default).
 */
public class MassReadDetector implements AnomalyDetector {

    public static final String TYPE = "mass_read";
    public static final String ACTION_SECURITY_EVENT = "security.event";

    private final List<AuditLogger> auditLoggers;
    private final int threshold;
    private final Set<String> readActionPrefixes;

    public MassReadDetector(List<AuditLogger> auditLoggers, int threshold) {
        this(auditLoggers, threshold, Set.of("data.read", "session.load", "model.inference"));
    }

    public MassReadDetector(List<AuditLogger> auditLoggers, int threshold, Set<String> readActionPrefixes) {
        this.auditLoggers = List.copyOf(auditLoggers == null ? List.of() : auditLoggers);
        this.threshold = threshold;
        this.readActionPrefixes = Set.copyOf(readActionPrefixes);
    }

    @Override
    public List<Anomaly> detect(String tenantId, Instant from, Instant to) {
        if (tenantId == null || tenantId.isBlank()) return List.of();
        // actor → distinct subjects touched in window
        Map<String, Set<String>> subjectsByActor = new HashMap<>();
        for (AuditLogger logger : auditLoggers) {
            for (AuditEvent e : logger.query(tenantId, Integer.MAX_VALUE)) {
                if (e.timestamp() == null) continue;
                if (from != null && e.timestamp().isBefore(from)) continue;
                if (to != null && !e.timestamp().isBefore(to)) continue;
                if (!isReadAction(e.action())) continue;
                subjectsByActor.computeIfAbsent(e.actor(), a -> new java.util.HashSet<>()).add(e.resource());
            }
        }
        List<Anomaly> anomalies = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : subjectsByActor.entrySet()) {
            if (entry.getValue().size() >= threshold) {
                Anomaly a = new Anomaly(TYPE, tenantId, entry.getKey(),
                        entry.getValue().size(), from, to,
                        "Actor " + entry.getKey() + " read " + entry.getValue().size()
                                + " distinct subjects — threshold " + threshold);
                anomalies.add(a);
                emit(a);
            }
        }
        return anomalies;
    }

    private boolean isReadAction(String action) {
        if (action == null) return false;
        for (String pref : readActionPrefixes) if (action.startsWith(pref)) return true;
        return false;
    }

    private void emit(Anomaly a) {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(a.windowEnd() == null ? Instant.now() : a.windowEnd())
                .tenantId(a.tenantId())
                .actor("system")
                .action(ACTION_SECURITY_EVENT)
                .resource("actor:" + a.subject())
                .outcome(AuditEvent.Outcome.DENIED)
                .details(Map.of("anomalyType", a.anomalyType(), "eventCount", a.eventCount(),
                        "description", a.description()))
                .build();
        for (AuditLogger logger : auditLoggers) {
            try {
                logger.log(event);
            } catch (RuntimeException ignored) {
                // Never let a broken audit sink swallow anomaly detection itself.
            }
        }
    }
}
