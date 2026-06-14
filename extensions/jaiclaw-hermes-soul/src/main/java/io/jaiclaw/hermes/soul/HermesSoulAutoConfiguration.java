package io.jaiclaw.hermes.soul;

import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.hermes.soul.store.HermesStoreProvider;
import io.jaiclaw.hermes.soul.store.JsonHermesStoreProvider;
import io.jaiclaw.hermes.soul.user.HermesUserKeyResolver;
import io.jaiclaw.hermes.soul.user.IdentityLinkUserKeyResolver;
import io.jaiclaw.identity.IdentityResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

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
        // (SoulPromptInjector, agent tool, REST controllers, MCP providers,
        // actuator counters) as later tasks land.
        return "hermes-soul-enabled";
    }

    @Bean
    @ConditionalOnMissingBean
    public HermesUserKeyResolver hermesUserKeyResolver(ObjectProvider<IdentityResolver> identityResolver) {
        return new IdentityLinkUserKeyResolver(identityResolver.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public HermesStoreProvider hermesStoreProvider(HermesSoulProperties props,
                                                   ObjectProvider<TenantGuard> tenantGuard) {
        return new JsonHermesStoreProvider(Path.of(props.rootDir()), tenantGuard.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public SoulProvider soulProvider(HermesStoreProvider storeProvider) {
        return storeProvider.soulStore();
    }
}
