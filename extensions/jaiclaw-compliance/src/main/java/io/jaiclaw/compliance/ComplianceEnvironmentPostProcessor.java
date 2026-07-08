package io.jaiclaw.compliance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Runs before Spring auto-config to translate {@code jaiclaw.compliance.profile}
 * + explicit {@code jaiclaw.compliance.<flag>} overrides into a stable
 * {@code jaiclaw.compliance.effective.<flag>} namespace that every conditional
 * bean anywhere in the reactor keys off of.
 *
 * <p>Also — for the two flags that live in other subsystems' config
 * ({@code jaiclaw.security.require-https}) — sets the downstream property
 * when compliance mode requires it, so the operator only has to think about
 * one config surface.
 *
 * <p>Precedence (Option 1 from the design discussion):
 * <ol>
 *   <li>Explicit {@code jaiclaw.compliance.<flag>} wins if set to true/false.</li>
 *   <li>{@code jaiclaw.compliance.profile} default applies otherwise.</li>
 *   <li>A pre-existing value on the mapped-through property
 *       (e.g. {@code jaiclaw.security.require-https}) also wins, so an
 *       operator can opt out at the target subsystem too.</li>
 * </ol>
 *
 * <p>The post-processor writes into a dedicated {@code MapPropertySource}
 * placed near the top of the environment so its values are visible to
 * {@code @ConditionalOnProperty} evaluation. It does NOT overwrite an
 * explicit value on the target property (option-1 semantics).
 */
public class ComplianceEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ComplianceEnvironmentPostProcessor.class);

    static final String SOURCE_NAME = "jaiclawComplianceEffective";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String profileRaw = env.getProperty("jaiclaw.compliance.profile", "none");
        ComplianceProfile profile = parseProfile(profileRaw);
        ComplianceProperties props = new ComplianceProperties(
                profile,
                env.getProperty("jaiclaw.compliance.require-https", Boolean.class),
                env.getProperty("jaiclaw.compliance.retention-enforcement", Boolean.class),
                env.getProperty("jaiclaw.compliance.audit-chat-client", Boolean.class),
                env.getProperty("jaiclaw.compliance.baa-warnings", Boolean.class),
                env.getProperty("jaiclaw.compliance.prompt-redaction", Boolean.class)
        );

        java.util.Map<String, Object> effective = new java.util.LinkedHashMap<>(props.asEffectiveProperties());

        // Cross-subsystem property: jaiclaw.security.require-https. Only set
        // it when unset by the operator — don't overwrite an explicit choice.
        if (env.getProperty("jaiclaw.security.require-https") == null && props.effectiveRequireHttps()) {
            effective.put("jaiclaw.security.require-https", true);
        }

        env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, effective));
        if (profile != ComplianceProfile.NONE) {
            log.info("Compliance profile '{}' active — effective flags: httpsGuard={}, retention={}, chatAudit={}, baaWarn={}, promptRedact={}",
                    profile,
                    props.effectiveRequireHttps(),
                    props.effectiveRetentionEnforcement(),
                    props.effectiveAuditChatClient(),
                    props.effectiveBaaWarnings(),
                    props.effectivePromptRedaction());
        }
    }

    private static ComplianceProfile parseProfile(String raw) {
        if (raw == null || raw.isBlank()) return ComplianceProfile.NONE;
        try {
            return ComplianceProfile.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown jaiclaw.compliance.profile='{}' — falling back to NONE", raw);
            return ComplianceProfile.NONE;
        }
    }
}
