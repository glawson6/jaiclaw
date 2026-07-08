package io.jaiclaw.compliance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compliance orchestration configuration. Read by
 * {@link ComplianceEnvironmentPostProcessor} to derive the effective
 * {@code jaiclaw.compliance.effective.*} flags.
 *
 * <p>Each flag is a nullable {@link Boolean}. Null = "no explicit override,
 * use the profile default". True/false = "override the profile".
 *
 * @param profile              profile bundle (default {@link ComplianceProfile#NONE})
 * @param requireHttps         explicit override for HTTPS enforcement
 * @param retentionEnforcement explicit override for retention enforcement
 * @param auditChatClient      explicit override for LLM-call audit decorator
 * @param baaWarnings          explicit override for BAA-eligible provider warnings
 * @param promptRedaction      explicit override for pre-LLM prompt redaction (T2)
 */
public record ComplianceProperties(
        ComplianceProfile profile,
        Boolean requireHttps,
        Boolean retentionEnforcement,
        Boolean auditChatClient,
        Boolean baaWarnings,
        Boolean promptRedaction
) {

    public ComplianceProperties {
        if (profile == null) profile = ComplianceProfile.NONE;
    }

    /** Explicit override wins; otherwise profile default. */
    public boolean effectiveRequireHttps() {
        return requireHttps != null ? requireHttps : profile.requiresHttps();
    }

    public boolean effectiveRetentionEnforcement() {
        return retentionEnforcement != null ? retentionEnforcement : profile.requiresRetentionEnforcement();
    }

    public boolean effectiveAuditChatClient() {
        return auditChatClient != null ? auditChatClient : profile.requiresAuditChatClient();
    }

    public boolean effectiveBaaWarnings() {
        return baaWarnings != null ? baaWarnings : profile.requiresBaaWarnings();
    }

    public boolean effectivePromptRedaction() {
        return promptRedaction != null ? promptRedaction : profile.requiresPromptRedaction();
    }

    /**
     * Serialize as a map for the {@code MapPropertySource} the post-processor
     * pushes onto the environment. Only writes the {@code effective.*} keys —
     * the raw profile / flag values are already on the environment via the
     * operator's config.
     */
    public Map<String, Object> asEffectiveProperties() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jaiclaw.compliance.effective.profile", profile.name().toLowerCase());
        out.put("jaiclaw.compliance.effective.require-https", effectiveRequireHttps());
        out.put("jaiclaw.compliance.effective.retention-enforcement", effectiveRetentionEnforcement());
        out.put("jaiclaw.compliance.effective.audit-chat-client", effectiveAuditChatClient());
        out.put("jaiclaw.compliance.effective.baa-warnings", effectiveBaaWarnings());
        out.put("jaiclaw.compliance.effective.prompt-redaction", effectivePromptRedaction());
        return out;
    }
}
