package io.jaiclaw.autoconfigure;

import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Tenant configuration foundation.
 *
 * <p>Beans defined here:
 * <ul>
 *   <li>{@link TenantProperties} — bound from {@code jaiclaw.tenant.*},
 *       carries mode (SINGLE/MULTI), {@code defaultTenantId}, and strict-mode
 *       validation flag.</li>
 *   <li>{@link TenantGuard} — central tenant resolution utility, used by every
 *       downstream component that needs tenant-aware key construction.
 *       Construction emits the opaque-default WARN when
 *       {@code defaultTenantId} is still the placeholder {@code "default"}.</li>
 * </ul>
 *
 * <p>Runs second in the DAG, after {@link JaiClawHttpAutoConfiguration}. Most
 * downstream auto-configs depend on {@link TenantGuard}, so this must be in
 * place before them.
 *
 * <p>Carved out of the former {@code JaiClawAutoConfiguration} monolith
 * (audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.4, Phase 3 P3.4).
 */
@AutoConfiguration(after = JaiClawHttpAutoConfiguration.class)
@EnableConfigurationProperties(JaiClawProperties.class)
public class JaiClawTenantAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawTenantAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TenantProperties tenantProperties(JaiClawProperties properties) {
        var cfg = properties.tenant();
        return new TenantProperties(cfg.mode(), cfg.defaultTenantId(), cfg.strictDefaultTenantId());
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantGuard tenantGuard(TenantProperties tenantProperties) {
        TenantGuard guard = new TenantGuard(tenantProperties);
        if (guard.isMultiTenant()) {
            log.debug("JaiClaw tenant mode: MULTI — strict isolation enabled");
        } else {
            log.debug("JaiClaw tenant mode: SINGLE — shared data space");
        }
        // Security: the SINGLE-mode storage-key prefix is sourced from
        // TenantProperties#defaultTenantId. If it's still the literal
        // placeholder "default", an attacker who can influence tenant-id
        // headers could probe predictable namespaces. Operators must
        // override jaiclaw.tenant.default-tenant-id in production. The
        // hard guard is jaiclaw.tenant.strict-default-tenant-id=true; this
        // log is a softer reminder, kept at DEBUG so quiet CLI runs aren't
        // spammed every command.
        if (!guard.isMultiTenant() && tenantProperties.isUsingPlaceholderDefaultTenantId()) {
            log.debug("jaiclaw.tenant.default-tenant-id is still the literal string 'default'. "
                    + "In SINGLE mode this value is used as the storage-key prefix. Set this "
                    + "property to a high-entropy value (e.g., a UUID) before exposing this app "
                    + "to untrusted callers, otherwise an attacker who can influence tenant-id "
                    + "headers may probe keys under the predictable 'default:' namespace. "
                    + "Set jaiclaw.tenant.strict-default-tenant-id=true to make weak values "
                    + "an outright startup failure.");
        }
        return guard;
    }
}
