package io.jaiclaw.compliance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
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
                env.getProperty("jaiclaw.compliance.prompt-redaction", Boolean.class),
                env.getProperty("jaiclaw.compliance.fips-enforced", Boolean.class),
                env.getProperty("jaiclaw.compliance.fedramp-warnings", Boolean.class),
                env.getProperty("jaiclaw.compliance.cui-warnings", Boolean.class),
                env.getProperty("jaiclaw.compliance.audit-hash-chain", Boolean.class),
                env.getProperty("jaiclaw.compliance.encrypt-at-rest", Boolean.class),
                env.getProperty("jaiclaw.compliance.hardened-tool-profile", Boolean.class),
                env.getProperty("jaiclaw.compliance.rate-limit", Boolean.class)
        );

        java.util.Map<String, Object> effective = new java.util.LinkedHashMap<>(props.asEffectiveProperties());

        // Cross-subsystem properties. Each is only set when the operator has not
        // chosen one — a profile is a default bundle, never an override of an
        // explicit instruction.
        if (env.getProperty("jaiclaw.security.require-https") == null && props.effectiveRequireHttps()) {
            effective.put("jaiclaw.security.require-https", true);
        }
        // Narrows the fail-open FULL default. Written as the literal enum name
        // because JaiClawGatewayAutoConfiguration reads this as a raw string and
        // fails startup on an unparseable value.
        if (env.getProperty("jaiclaw.security.default-tool-profile") == null
                && props.effectiveHardenedToolProfile()) {
            effective.put("jaiclaw.security.default-tool-profile", "MINIMAL");
        }
        if (env.getProperty("jaiclaw.security.rate-limit.enabled") == null
                && props.effectiveRateLimit()) {
            effective.put("jaiclaw.security.rate-limit.enabled", true);
        }

        env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, effective));
        if (profile != ComplianceProfile.NONE) {
            log.info("Compliance profile '{}' active — effective flags: httpsGuard={}, retention={}, chatAudit={}, baaWarn={}, promptRedact={}, auditHashChain={}, encryptAtRest={}, hardenedToolProfile={}, rateLimit={}, fipsEnforced={}, fedrampWarn={}, cuiWarn={}",
                    profile,
                    props.effectiveRequireHttps(),
                    props.effectiveRetentionEnforcement(),
                    props.effectiveAuditChatClient(),
                    props.effectiveBaaWarnings(),
                    props.effectivePromptRedaction(),
                    props.effectiveAuditHashChain(),
                    props.effectiveEncryptAtRest(),
                    props.effectiveHardenedToolProfile(),
                    props.effectiveRateLimit(),
                    props.effectiveFipsEnforced(),
                    props.effectiveFedrampWarnings(),
                    props.effectiveCuiWarnings());

            // An operator who asked for encryption deserves to know at startup
            // whether it is actually on, not to discover plaintext later.
            if (props.effectiveEncryptAtRest()
                    && env.getProperty("jaiclaw.compliance.encryption.key") == null) {
                log.warn("Compliance profile '{}' enables encryption at rest but "
                        + "jaiclaw.compliance.encryption.key is not set in the environment. "
                        + "Startup will fail unless a SecretsProvider supplies it.", profile);
            }
        }
    }

    private static ComplianceProfile parseProfile(String raw) {
        if (raw == null || raw.isBlank()) return ComplianceProfile.NONE;
        try {
            // Normalize kebab-case ("fedramp-moderate", "cmmc-l2") to enum SCREAMING_SNAKE_CASE
            String normalized = raw.trim().toUpperCase().replace('-', '_');
            return ComplianceProfile.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown jaiclaw.compliance.profile='{}' — falling back to NONE", raw);
            return ComplianceProfile.NONE;
        }
    }
}
