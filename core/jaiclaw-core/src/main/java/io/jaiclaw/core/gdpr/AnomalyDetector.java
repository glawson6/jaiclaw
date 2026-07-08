package io.jaiclaw.core.gdpr;

import io.jaiclaw.core.api.Stable;

import java.time.Instant;
import java.util.List;

/**
 * T3-3 — SPI for anomaly / breach-signal detection over the audit trail.
 *
 * <p>Implementations run scheduled scans over the audit stream and emit
 * {@code security.event} audit entries when suspicious patterns are found.
 * Actual alerting (email, PagerDuty, SIEM integration) is external — the
 * framework's job is to emit structured signals.
 *
 * <p>Contract:
 * <ul>
 *   <li>Detectors MUST be tenant-scoped — a scan for tenant A can't see
 *       tenant B's events.</li>
 *   <li>Each detected anomaly SHOULD carry enough context (actor, subject,
 *       time range, count) for an operator to triage without re-querying the
 *       audit store.</li>
 *   <li>Detectors SHOULD be idempotent over a given window — running the
 *       same scan twice must not produce duplicate {@code security.event}
 *       entries. Impls handle this via a de-dup key or a low-watermark.</li>
 * </ul>
 */
@Stable
public interface AnomalyDetector {

    /**
     * Run one detection pass over {@code tenantId}'s audit events in the
     * {@code from..to} window. Returns the anomalies found; impls SHOULD
     * also write them to their AuditLogger as {@code security.event}
     * entries so downstream SIEM ingestion picks them up.
     */
    List<Anomaly> detect(String tenantId, Instant from, Instant to);

    /**
     * A single anomaly observation.
     *
     * @param anomalyType  the detector class (e.g. {@code "mass_read"},
     *                     {@code "bulk_export"}, {@code "off_hours_access"})
     * @param tenantId     tenant the events belong to
     * @param subject      subject of the anomaly (actor / dataSubjectId /
     *                     resource — detector-specific)
     * @param eventCount   number of underlying events that contributed
     * @param windowStart  window start of the anomaly
     * @param windowEnd    window end of the anomaly
     * @param description  human-readable summary suitable for alert body
     */
    record Anomaly(
            String anomalyType,
            String tenantId,
            String subject,
            long eventCount,
            Instant windowStart,
            Instant windowEnd,
            String description
    ) {}
}
