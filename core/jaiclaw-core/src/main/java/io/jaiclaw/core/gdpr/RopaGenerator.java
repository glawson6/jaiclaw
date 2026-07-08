package io.jaiclaw.core.gdpr;

import io.jaiclaw.core.api.Stable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * T3-1 — GDPR Art. 30 Record of Processing Activities generator.
 *
 * <p>Reconstructs a per-tenant RoPA from the audit trail + tenant configuration.
 * The output is a machine-readable JSON structure that can be fed to a PDF
 * template or a compliance-management tool.
 *
 * <p>Contract:
 * <ul>
 *   <li>Generation MUST be tenant-scoped — a RoPA for tenant A never
 *       references tenant B's events or configuration.</li>
 *   <li>The record covers a caller-supplied window ({@code from}, {@code to});
 *       impls SHOULD default {@code from} to 30 days ago and {@code to} to
 *       "now" when either is null.</li>
 *   <li>Processing activities are grouped by {@code action} (e.g.
 *       {@code model.inference.request}, {@code data.subject_erasure});
 *       recipients + data categories + lawful bases are aggregated per group
 *       from the underlying audit events.</li>
 * </ul>
 */
@Stable
public interface RopaGenerator {

    /**
     * Generate the RoPA for {@code tenantId} covering the {@code from..to} window.
     */
    Ropa generate(String tenantId, Instant from, Instant to);

    /**
     * The generated Record of Processing Activities.
     *
     * @param tenantId          tenant covered by this RoPA
     * @param from              start of window (inclusive)
     * @param to                end of window (exclusive)
     * @param generatedAt       when the RoPA was assembled
     * @param controllerName    the data controller (tenant display name)
     * @param processorName     "JaiClaw framework"
     * @param activities        one entry per processing-activity group
     */
    record Ropa(
            String tenantId,
            Instant from,
            Instant to,
            Instant generatedAt,
            String controllerName,
            String processorName,
            List<ProcessingActivity> activities
    ) {
        public Ropa {
            if (tenantId == null || tenantId.isBlank())
                throw new IllegalArgumentException("tenantId must not be blank");
            if (generatedAt == null) generatedAt = Instant.now();
            activities = activities == null ? List.of() : List.copyOf(activities);
        }
    }

    /**
     * A single processing-activity group in the RoPA.
     *
     * @param action           the audit-event action (e.g. {@code model.inference.request})
     * @param eventCount       how many events were rolled up into this activity
     * @param lawfulBases      distinct lawful bases seen in the window
     * @param dataCategories   distinct data categories touched
     * @param recipients       distinct recipients (LLM providers, external services)
     * @param retentionSummary map of {@code lawfulBasis} → longest retention seen
     */
    record ProcessingActivity(
            String action,
            long eventCount,
            List<String> lawfulBases,
            List<String> dataCategories,
            List<String> recipients,
            Map<String, Integer> retentionSummary
    ) {}
}
