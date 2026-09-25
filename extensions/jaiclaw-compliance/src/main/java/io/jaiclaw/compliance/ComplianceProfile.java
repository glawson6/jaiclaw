package io.jaiclaw.compliance;

/**
 * Compliance profile — a coherent bundle of individual compliance flags.
 *
 * <p>The profile is a convenience knob. Downstream code never reads the
 * profile directly; it reads the {@code jaiclaw.compliance.effective.*}
 * flags that the {@link ComplianceEnvironmentPostProcessor} derives from
 * the profile + explicit overrides. This keeps the conditional wiring
 * flat and lets an operator inspect exactly what the runtime is doing.
 *
 * <p>Semantics per profile (defaults; individual flags override):
 * <table>
 *   <caption>Profile → flag defaults</caption>
 *   <tr><th>Flag</th><th>NONE</th><th>GDPR</th><th>HIPAA</th><th>BOTH</th><th>FEDRAMP_MODERATE</th><th>CMMC_L2</th><th>FIPS</th></tr>
 *   <tr><td>require-https</td>        <td>false</td><td>true</td><td>true</td><td>true</td><td>true</td><td>true</td><td>true</td><td>false</td></tr>
 *   <tr><td>retention-enforcement</td> <td>false</td><td>true</td><td>true</td><td>true</td><td>true</td><td>true</td><td>true</td><td>false</td></tr>
 *   <tr><td>audit-chat-client</td>    <td>false</td><td>true</td><td>true</td><td>true</td><td>true</td><td>true</td><td>true</td><td>false</td></tr>
 *   <tr><td>baa-warnings</td>         <td>false</td><td>false</td><td>true</td><td>true</td><td>false</td><td>false</td><td>false</td><td>false</td></tr>
 *   <tr><td>prompt-redaction (T2)</td> <td>false</td><td>false</td><td>true</td><td>true</td><td>false</td><td>false</td><td>true</td><td>false</td></tr>
 *   <tr><td>audit-hash-chain</td>     <td>false</td><td>false</td><td>false</td><td>false</td><td>true</td><td>false</td><td>false</td><td>false</td></tr>
 *   <tr><td>encrypt-at-rest</td>      <td>false</td><td>false</td><td>false</td><td>false</td><td>true</td><td>false</td><td>false</td><td>false</td></tr>
 *   <tr><td>hardened-tool-profile</td> <td>false</td><td>false</td><td>false</td><td>false</td><td>true</td><td>false</td><td>false</td><td>false</td></tr>
 *   <tr><td>rate-limit</td>           <td>false</td><td>false</td><td>false</td><td>false</td><td>true</td><td>false</td><td>false</td><td>false</td></tr>
 *   <tr><td>fips-enforced</td>        <td>false</td><td>false</td><td>false</td><td>false</td><td>false</td><td>true</td><td>false</td><td>true</td></tr>
 *   <tr><td>fedramp-warnings</td>     <td>false</td><td>false</td><td>false</td><td>false</td><td>false</td><td>true</td><td>false</td><td>false</td></tr>
 *   <tr><td>cui-warnings</td>         <td>false</td><td>false</td><td>false</td><td>false</td><td>false</td><td>false</td><td>true</td><td>false</td></tr>
 * </table>
 *
 * <p><strong>Deployment note.</strong> A tenant subject to full GDPR or HIPAA
 * typically will not share persistence, audit logs, or LLM configuration
 * with other tenants — the profile is a per-deployment decision, not
 * per-request. A fully-compliant tenant gets its own instance.
 */
public enum ComplianceProfile {
    /** No compliance behavior beyond the framework defaults. */
    NONE,
    /** GDPR-focused: HTTPS enforcement, retention, LLM-call auditing. */
    GDPR,
    /** HIPAA-focused: GDPR baseline + BAA warnings + prompt redaction (T2). */
    HIPAA,
    /** Both GDPR + HIPAA. */
    BOTH,
    /**
     * SOC 2 (AICPA Trust Services Criteria) — the hardened commercial posture.
     *
     * <p>HTTPS + retention + LLM-call audit, plus the four controls an auditor
     * asks for that no other profile enables: a tamper-evident audit chain
     * (CC7.2), encryption at rest (C1), a narrowed default tool profile (CC6.3),
     * and rate limiting (CC6.6).
     *
     * <p>Does <strong>not</strong> enable BAA, FedRAMP, CUI or FIPS flags —
     * those are regime-specific. A FISMA Moderate deployment uses this profile
     * plus {@code jaiclaw.compliance.fips-enforced=true}, which covers the
     * posture recommended in
     * {@code docs/FEDERAL-COMPLIANCE-ASSESSMENT-2026-08-06.md}.
     *
     * <p>Nor does it enable prompt redaction: {@code RegexPromptRedactor} has no
     * framework call site and is wired non-strict, so it is inert outside PHI
     * processing. Enabling it would imply a control that does not operate.
     *
     * <p><strong>Requires an encryption key.</strong> Startup aborts when
     * {@code jaiclaw.compliance.encryption.key} is unset — see
     * {@code EncryptionKeyResolver}.
     */
    SOC2,
    /**
     * FedRAMP Moderate baseline: HTTPS + retention + audit + FIPS crypto
     * enforcement + FedRAMP-authorized-provider warnings. Does NOT enable
     * BAA or PHI redaction (those are HIPAA-specific).
     */
    FEDRAMP_MODERATE,
    /**
     * CMMC Level 2 (DoD contractors handling CUI). HTTPS + retention +
     * audit + CUI-authorized-provider warnings + prompt redaction. Does
     * NOT enable BAA or FedRAMP flags — CMMC has its own boundary.
     */
    CMMC_L2,
    /**
     * Standalone FIPS enforcement — enables the {@code FipsPostureStartupCheck}
     * without any other compliance flag. For deployments that need FIPS
     * crypto module verification but manage other compliance concerns
     * externally.
     */
    FIPS;

    /** @return true when this profile turns on {@code require-https} by default. */
    public boolean requiresHttps() {
        return this == GDPR || this == HIPAA || this == BOTH || this == SOC2
                || this == FEDRAMP_MODERATE || this == CMMC_L2;
    }

    /** @return true when this profile turns on retention enforcement by default. */
    public boolean requiresRetentionEnforcement() {
        return this == GDPR || this == HIPAA || this == BOTH || this == SOC2
                || this == FEDRAMP_MODERATE || this == CMMC_L2;
    }

    /** @return true when this profile turns on the LLM-call audit decorator by default. */
    public boolean requiresAuditChatClient() {
        return this == GDPR || this == HIPAA || this == BOTH || this == SOC2
                || this == FEDRAMP_MODERATE || this == CMMC_L2;
    }

    /** @return true when this profile turns on BAA warnings by default (HIPAA / BOTH). */
    public boolean requiresBaaWarnings() {
        return this == HIPAA || this == BOTH;
    }

    /** @return true when this profile turns on {@code PromptRedactor} (T2) by default (HIPAA / BOTH / CMMC_L2). */
    public boolean requiresPromptRedaction() {
        return this == HIPAA || this == BOTH || this == CMMC_L2;
    }

    /**
     * @return true when this profile turns on the FIPS posture startup
     * check by default (FEDRAMP_MODERATE / FIPS).
     */
    public boolean requiresFipsEnforced() {
        return this == FEDRAMP_MODERATE || this == FIPS;
    }

    /**
     * @return true when this profile turns on the FedRAMP-authorized
     * provider warning decorator by default (FEDRAMP_MODERATE).
     */
    public boolean requiresFedrampWarnings() {
        return this == FEDRAMP_MODERATE;
    }

    /**
     * @return true when this profile turns on the CUI-authorized
     * provider warning decorator by default (CMMC_L2).
     */
    public boolean requiresCuiWarnings() {
        return this == CMMC_L2;
    }

    /**
     * @return true when this profile turns on the tamper-evident audit chain
     * ({@code HashChainedAuditLogger}) by default (SOC2). This is the control
     * that answers "how do you know these logs were not altered?" — SOC 2 CC7.2.
     */
    public boolean requiresAuditHashChain() {
        return this == SOC2;
    }

    /**
     * @return true when this profile turns on encryption at rest by default
     * (SOC2). <strong>Startup aborts when no key is resolvable</strong> — a
     * deployment that believes it encrypts but does not is worse off than one
     * that refuses to boot.
     */
    public boolean requiresEncryptAtRest() {
        return this == SOC2;
    }

    /**
     * @return true when this profile narrows
     * {@code jaiclaw.security.default-tool-profile} to {@code MINIMAL} by
     * default (SOC2). The framework default is {@code FULL} through 1.2.x,
     * which fails open — see SOC 2 CC6.3.
     */
    public boolean requiresHardenedToolProfile() {
        return this == SOC2;
    }

    /**
     * @return true when this profile turns on the built-in rate limiter by
     * default (SOC2) — SOC 2 CC6.6.
     */
    public boolean requiresRateLimit() {
        return this == SOC2;
    }
}
