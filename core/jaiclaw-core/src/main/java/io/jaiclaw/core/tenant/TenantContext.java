package io.jaiclaw.core.tenant;

import io.jaiclaw.core.api.Stable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the current tenant (e.g., a coaching program) for multi-tenant isolation.
 * Every inbound request must resolve to a TenantContext before any agent execution,
 * memory access, or tool call occurs.
 *
 * <p>0.8.0 P3.5: {@link Stable} — the multi-tenant hardening work in
 * 0.7.x locked this surface.
 *
 * <p>0.9.4 (T1-1): compliance metadata accessors. All optional, all
 * default-implemented over the existing {@link #getMetadata()} map so
 * existing consumers keep working unchanged. Reserved metadata keys:
 * <ul>
 *   <li>{@value #KEY_LAWFUL_BASIS} — GDPR Art. 6 lawful basis (string)</li>
 *   <li>{@value #KEY_RETENTION_DAYS} — data retention in days (integer)</li>
 *   <li>{@value #KEY_RESTRICTION_FLAGS} — comma-separated restriction flags (string)
 *       or {@link Set} of strings</li>
 *   <li>{@value #KEY_DATA_RESIDENCY} — required residency (e.g. "eu-west", string)</li>
 *   <li>{@value #KEY_PHI_PROCESSING} — true when tenant handles PHI (boolean)</li>
 *   <li>{@value #KEY_CONSENT_TOKEN} — reference to a consent record (string)</li>
 * </ul>
 */
@Stable
public interface TenantContext {

    /** Metadata key: GDPR Art. 6 lawful basis. Value: string. */
    String KEY_LAWFUL_BASIS = "gdpr.lawful_basis";

    /** Metadata key: data retention in days. Value: integer / long / string parseable as int. */
    String KEY_RETENTION_DAYS = "data.retention_days";

    /** Metadata key: processing restriction flags. Value: {@code Set<String>} or comma-separated string. */
    String KEY_RESTRICTION_FLAGS = "data.restriction_flags";

    /** Metadata key: required data residency (e.g. "eu-west", "us-east"). Value: string. */
    String KEY_DATA_RESIDENCY = "data.residency_required";

    /** Metadata key: true when this tenant handles PHI (HIPAA §164.502). Value: boolean or string. */
    String KEY_PHI_PROCESSING = "hipaa.phi_processing";

    /** Metadata key: consent record reference used by {@code ConsentManager} in T2-5. Value: string. */
    String KEY_CONSENT_TOKEN = "gdpr.consent_token";

    /**
     * Unique tenant identifier (e.g., programId UUID).
     */
    String getTenantId();

    /**
     * Human-readable tenant name (e.g., "University of Georgia Football").
     */
    String getTenantName();

    /**
     * Arbitrary metadata associated with the tenant — subscription tier, sport, division, etc.
     */
    Map<String, Object> getMetadata();

    // ── Compliance metadata (T1-1) ─────────────────────────────────────────
    //
    // Every accessor below returns null / empty when the key isn't set, so
    // existing consumers see no change. Downstream compliance code
    // (retention purge, BAA enforcement, PromptRedactor, audit decorator)
    // reads these to drive per-tenant behavior.

    /**
     * Optional GDPR Art. 6 lawful basis for processing this tenant's data.
     * Common values: {@code "consent"}, {@code "contract"},
     * {@code "legal_obligation"}, {@code "vital_interests"},
     * {@code "public_task"}, {@code "legitimate_interests"}.
     *
     * @return the declared lawful basis, or {@code null} when unset
     */
    default String getLawfulBasis() {
        return metadataString(KEY_LAWFUL_BASIS);
    }

    /**
     * Optional data-retention limit in days. When set, the T1-6
     * {@code RetentionEnforcementService} purges transcripts, memory, and
     * audit entries older than this. When {@code null}, unlimited retention
     * applies (framework default).
     *
     * <p>HIPAA §164.316(b)(2) requires audit retention ≥ 6 years — set this
     * to at least {@code 6 * 365 = 2190} for tenants handling PHI.
     *
     * @return retention in days, or {@code null} when unlimited
     */
    default Integer getRetentionDays() {
        Object raw = getMetadata() != null ? getMetadata().get(KEY_RETENTION_DAYS) : null;
        if (raw == null) return null;
        if (raw instanceof Integer i) return i;
        if (raw instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Optional set of processing restriction flags (GDPR Art. 18). Common
     * flags: {@code "no_llm_calls"}, {@code "no_memory_writes"},
     * {@code "no_audit_export"}. When present, downstream code MUST honor
     * them by short-circuiting the disallowed operation.
     *
     * @return restriction flags, or empty set when none
     */
    default Set<String> getRestrictionFlags() {
        Object raw = getMetadata() != null ? getMetadata().get(KEY_RESTRICTION_FLAGS) : null;
        if (raw == null) return Set.of();
        if (raw instanceof Set<?> s) {
            Set<String> out = new LinkedHashSet<>();
            for (Object o : s) {
                if (o != null) out.add(o.toString());
            }
            return Collections.unmodifiableSet(out);
        }
        if (raw instanceof String s && !s.isBlank()) {
            Set<String> out = new LinkedHashSet<>();
            for (String flag : s.split(",")) {
                String trimmed = flag.trim();
                if (!trimmed.isEmpty()) out.add(trimmed);
            }
            return Collections.unmodifiableSet(out);
        }
        return Set.of();
    }

    /**
     * True when a specific restriction flag is set on this tenant.
     */
    default boolean hasRestriction(String flag) {
        return flag != null && getRestrictionFlags().contains(flag);
    }

    /**
     * Optional data-residency requirement (e.g. {@code "eu-west"},
     * {@code "us-east"}). When set, downstream code (LLM provider
     * resolution, storage backend selection) SHOULD refuse to route
     * data outside the declared region.
     *
     * @return required residency, or {@code null} when unconstrained
     */
    default String getDataResidencyRequired() {
        return metadataString(KEY_DATA_RESIDENCY);
    }

    /**
     * True when this tenant handles Protected Health Information (HIPAA
     * §164.502). Drives the T1-4 BAA-eligible-provider enforcement and
     * the T2-3 {@code PromptRedactor} activation. Defaults to false so
     * non-PHI deployments aren't paying for redaction.
     */
    default boolean isPhiProcessing() {
        Object raw = getMetadata() != null ? getMetadata().get(KEY_PHI_PROCESSING) : null;
        if (raw == null) return false;
        if (raw instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(raw).trim());
    }

    /**
     * Optional reference to a {@code ConsentManager} record (T2-5) — a
     * short opaque id the framework can use to look up the current consent
     * status. The token is intentionally opaque: implementations differ
     * (JWT-embedded, database row, external service), so treat this as a
     * pointer rather than a payload.
     *
     * @return the consent-record identifier, or {@code null} when unset
     */
    default String getConsentToken() {
        return metadataString(KEY_CONSENT_TOKEN);
    }

    /** Internal helper: pull a string value from the metadata map, tolerating null map + null value. */
    private String metadataString(String key) {
        Map<String, Object> m = getMetadata();
        if (m == null) return null;
        Object v = m.get(key);
        return v == null ? null : Objects.toString(v, null);
    }
}
