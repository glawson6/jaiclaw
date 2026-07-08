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
 *   <tr><th>Flag</th><th>NONE</th><th>GDPR</th><th>HIPAA</th><th>BOTH</th></tr>
 *   <tr><td>require-https</td>       <td>false</td><td>true</td><td>true</td><td>true</td></tr>
 *   <tr><td>retention-enforcement</td><td>false</td><td>true</td><td>true</td><td>true</td></tr>
 *   <tr><td>audit-chat-client</td>   <td>false</td><td>true</td><td>true</td><td>true</td></tr>
 *   <tr><td>baa-warnings</td>        <td>false</td><td>false</td><td>true</td><td>true</td></tr>
 *   <tr><td>prompt-redaction (T2)</td><td>false</td><td>false</td><td>true</td><td>true</td></tr>
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
    /** Both frameworks — sum of GDPR + HIPAA. */
    BOTH;

    /** @return true when this profile turns on {@code require-https} by default. */
    public boolean requiresHttps() {
        return this != NONE;
    }

    /** @return true when this profile turns on retention enforcement by default. */
    public boolean requiresRetentionEnforcement() {
        return this != NONE;
    }

    /** @return true when this profile turns on the LLM-call audit decorator by default. */
    public boolean requiresAuditChatClient() {
        return this != NONE;
    }

    /** @return true when this profile turns on BAA warnings by default (HIPAA / BOTH). */
    public boolean requiresBaaWarnings() {
        return this == HIPAA || this == BOTH;
    }

    /** @return true when this profile turns on {@code PromptRedactor} (T2) by default (HIPAA / BOTH). */
    public boolean requiresPromptRedaction() {
        return this == HIPAA || this == BOTH;
    }
}
