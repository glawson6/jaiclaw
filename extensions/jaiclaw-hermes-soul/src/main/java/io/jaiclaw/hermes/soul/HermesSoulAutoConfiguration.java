package io.jaiclaw.hermes.soul;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pillar-level autoconfig for the hermes Soul extension. Defaults OFF —
 * no beans are created until {@code jaiclaw.hermes.soul.enabled=true}.
 *
 * <p>Multi-tenancy conformance: the {@code FileSoulProvider} default impl
 * uses per-tenant subdirectory paths via {@code TenantGuard.resolveTenantPrefix()};
 * the {@code TenantSoulController} and {@code TenantSoulMcpToolProvider} both
 * forward {@code TenantContext} per repo rule. Cross-tenant + cross-user
 * read isolation is asserted by {@code HermesStoreIsolationSpec} in the
 * test suite.
 *
 * <p>Plan §5 task 1.2 — autoconfig scaffold. Tenant-scope sub-config gated
 * separately by {@code jaiclaw.hermes.soul.tenant.enabled=true}.
 */
@AutoConfiguration
@Configuration
@EnableConfigurationProperties(HermesSoulProperties.class)
@ConditionalOnProperty(prefix = "jaiclaw.hermes.soul", name = "enabled", havingValue = "true")
public class HermesSoulAutoConfiguration {

    @Bean
    public String hermesSoulModuleMarker() {
        // Sentinel bean. Specs assert its presence/absence to verify the
        // @ConditionalOnProperty gate. Replaced by concrete beans
        // (FileSoulProvider, SoulPromptInjector, etc.) as later tasks land.
        return "hermes-soul-enabled";
    }
}
