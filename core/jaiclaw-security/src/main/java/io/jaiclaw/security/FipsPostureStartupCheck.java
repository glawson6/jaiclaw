package io.jaiclaw.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Federal compliance (2026-08): startup check that verifies the effective
 * JCE provider list matches a FIPS-approved allowlist.
 *
 * <p>Motivation: FIPS 140-3 requires that all cryptography protecting
 * federal information runs through a FIPS-certified module. JaiClaw's
 * algorithms (AES-GCM-256, SHA-256, HMAC-SHA256, EC P-256, X25519) are
 * all FIPS-approved on paper, but the underlying <em>provider</em> that
 * implements them must also be certified. This check gives operators a
 * loud, early signal at boot when a non-FIPS provider slips onto the
 * classpath.
 *
 * <p>Default behavior (opt-in): {@code jaiclaw.compliance.effective.fips-enforced=false}
 * → check is a no-op. Set the property to {@code true} (or use the
 * {@code fips} or {@code fedramp-moderate} compliance profile) to
 * enforce.
 *
 * <p>The check does two things depending on the enforcement flag:
 * <ul>
 *   <li>{@code fips-enforced=false} (default): completely silent.</li>
 *   <li>{@code fips-enforced=true}: enumerates {@code Security.getProviders()},
 *       compares each provider name against an allowlist, and throws
 *       {@link IllegalStateException} on the first non-FIPS provider found.</li>
 * </ul>
 *
 * <p>The allowlist recognises:
 * <ul>
 *   <li>{@code BCFIPS} — BouncyCastle FIPS provider (recommended)</li>
 *   <li>{@code SunPKCS11-*} — SunPKCS11 wrapping a FIPS-certified module
 *       (e.g. NSS in RHEL FIPS mode)</li>
 *   <li>{@code SUN}, {@code SunEC}, {@code SunJSSE}, {@code SunJCE} — accepted
 *       only when the operator sets {@code jaiclaw.compliance.fips.trust-sun=true},
 *       intended for OS-level FIPS-mode Corretto / Zulu / Adoptium builds where
 *       the JDK vendor patches the default providers to route to a certified
 *       module. Operator explicitly attests to this.</li>
 * </ul>
 *
 * <p>Wired as a bean by {@code JaiClawSecurityAutoConfiguration}. Bean is
 * always constructed; {@link #enforce()} returns early when the flag is
 * off so there's zero runtime cost for adopters not opting in.
 *
 * <p>See {@code docs/compliance/fips-140-3.md} for the full algorithm
 * inventory, BC-FIPS integration steps, and RHEL / Corretto / Zulu /
 * Adoptium FIPS-mode instructions.
 */
public class FipsPostureStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(FipsPostureStartupCheck.class);

    private static final Set<String> FIPS_APPROVED_PROVIDER_PREFIXES = Set.of(
            "BCFIPS",           // BouncyCastle FIPS provider — the canonical answer
            "SunPKCS11-"        // SunPKCS11 wrapping a FIPS-certified underlying module
    );

    /**
     * When the operator sets {@code jaiclaw.compliance.fips.trust-sun=true},
     * they're attesting they're running on an OS-level FIPS-mode JDK
     * (RHEL FIPS mode, Corretto FIPS, Zulu FIPS) where the JDK vendor
     * has patched every default provider to route through a certified
     * module. JaiClaw cannot verify this at runtime — the whole
     * provider allowlist check is skipped with an INFO log.
     */

    private final Environment env;

    public FipsPostureStartupCheck(Environment env) {
        this.env = env;
    }

    /**
     * Called during Spring bean initialization. Throws if enforcement is
     * on and a non-FIPS provider is registered — unless the operator has
     * attested to OS-level FIPS mode via
     * {@code jaiclaw.compliance.fips.trust-sun=true}.
     */
    public void enforce() {
        boolean enforced = Boolean.TRUE.equals(
                env.getProperty("jaiclaw.compliance.effective.fips-enforced", Boolean.class));
        if (!enforced) {
            return;   // opt-in; default false — zero-cost when compliance module is idle
        }

        Provider[] providers = Security.getProviders();
        List<String> registered = new ArrayList<>(providers.length);
        for (Provider p : providers) {
            registered.add(p.getName() + " (v" + p.getVersionStr() + ")");
        }

        log.info("FIPS posture check: {} JCE provider(s) registered: {}",
                registered.size(), registered);

        // OS-level FIPS mode attestation. See class-level Javadoc.
        boolean trustSunProviders = Boolean.TRUE.equals(
                env.getProperty("jaiclaw.compliance.fips.trust-sun", Boolean.class));
        if (trustSunProviders) {
            log.info("FIPS posture check: jaiclaw.compliance.fips.trust-sun=true — operator has "
                    + "attested to OS-level FIPS mode; skipping provider-name allowlist check.");
            return;
        }

        // Strict mode: every registered provider must be on the FIPS allowlist
        // (BCFIPS or SunPKCS11-*). BC-FIPS is the recommended integration.
        List<String> nonFips = new ArrayList<>();
        for (Provider p : providers) {
            if (!isFipsApproved(p.getName())) {
                nonFips.add(p.getName());
            }
        }

        if (!nonFips.isEmpty()) {
            throw new IllegalStateException(
                    "jaiclaw.compliance.effective.fips-enforced=true but non-FIPS JCE "
                            + "provider(s) detected: " + nonFips + ". Refusing to start — "
                            + "federal cryptography must run through a FIPS 140-3 certified "
                            + "module. Remediation options: (1) add BouncyCastle FIPS "
                            + "(bc-fips 2.x) to the classpath and register it as the "
                            + "highest-priority provider, (2) run on a JDK with OS-level "
                            + "FIPS mode enabled (RHEL, Corretto FIPS, Zulu FIPS) and set "
                            + "jaiclaw.compliance.fips.trust-sun=true, or (3) disable "
                            + "enforcement by unsetting jaiclaw.compliance.fips-enforced. "
                            + "See docs/compliance/fips-140-3.md.");
        }

        log.info("FIPS posture check: all registered providers on the FIPS allowlist — check passed");
    }

    private boolean isFipsApproved(String providerName) {
        if (providerName == null) return false;
        for (String prefix : FIPS_APPROVED_PROVIDER_PREFIXES) {
            if (providerName.equals(prefix) || providerName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
