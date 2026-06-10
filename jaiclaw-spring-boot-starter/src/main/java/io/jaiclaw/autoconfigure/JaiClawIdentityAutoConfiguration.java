package io.jaiclaw.autoconfigure;

import io.jaiclaw.core.auth.AuthProfileConstants;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.identity.IdentityLinkService;
import io.jaiclaw.identity.IdentityLinkStore;
import io.jaiclaw.identity.IdentityResolver;
import io.jaiclaw.identity.auth.*;
import io.jaiclaw.identity.oauth.OAuthFlowManager;
import io.jaiclaw.identity.oauth.OAuthProviderConfig;
import io.jaiclaw.identity.secret.SecretProviderConfig;
import io.jaiclaw.identity.secret.SecretRefResolver;
import io.jaiclaw.identity.sync.ExternalCliSyncManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for JaiClaw Identity and OAuth modules.
 * Activates when {@code jaiclaw-identity} is on the classpath.
 */
@AutoConfiguration
@AutoConfigureAfter(JaiClawAgentAutoConfiguration.class)
@ConditionalOnClass(name = "io.jaiclaw.identity.auth.AuthProfileStoreManager")
@EnableConfigurationProperties(JaiClawIdentityAutoConfiguration.OAuthProperties.class)
public class JaiClawIdentityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawIdentityAutoConfiguration.class);

    @ConfigurationProperties(prefix = "jaiclaw.oauth")
    public record OAuthProperties(
            boolean enabled,
            String stateDir,
            String agentId,
            Map<String, OAuthProviderConfig> providers,
            boolean readOnly,
            boolean cliSyncEnabled,
            Map<String, SecretProviderConfig> secretProviders
    ) {
        public OAuthProperties {
            if (stateDir == null || stateDir.isBlank()) {
                stateDir = Path.of(System.getProperty("user.home"), AuthProfileConstants.DEFAULT_STATE_DIR).toString();
            }
            if (agentId == null || agentId.isBlank()) agentId = "default";
            if (providers == null) providers = Map.of();
            if (secretProviders == null) secretProviders = Map.of();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthProfileStoreManager authProfileStoreManager(OAuthProperties properties) {
        Path stateDir = Path.of(properties.stateDir());
        log.info("Auth profile store: {} (readOnly={})", stateDir, properties.readOnly());
        return new AuthProfileStoreManager(stateDir, properties.readOnly());
    }

    @Bean
    @ConditionalOnMissingBean
    public SecretRefResolver secretRefResolver(OAuthProperties properties) {
        return new SecretRefResolver(properties.secretProviders() != null
                ? properties.secretProviders() : Map.of());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "jaiclaw.oauth.cli-sync-enabled", matchIfMissing = true)
    public ExternalCliSyncManager externalCliSyncManager() {
        return new ExternalCliSyncManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProviderTokenRefresherRegistry tokenRefresherRegistry(
            ObjectProvider<List<TokenRefresher>> refreshers) {
        List<TokenRefresher> list = refreshers.getIfAvailable(List::of);
        return new ProviderTokenRefresherRegistry(list);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthProfileResolver authProfileResolver(AuthProfileStoreManager storeManager,
                                                    SecretRefResolver secretRefResolver,
                                                    ProviderTokenRefresherRegistry refresherRegistry) {
        return new AuthProfileResolver(storeManager, secretRefResolver, refresherRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionAuthProfileResolver sessionAuthProfileResolver(AuthProfileStoreManager storeManager) {
        return new SessionAuthProfileResolver(storeManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuthFlowManager oAuthFlowManager(OAuthProperties properties,
                                              AuthProfileStoreManager storeManager) {
        return new OAuthFlowManager(
                properties.providers() != null ? properties.providers() : Map.of(),
                storeManager);
    }

    // --- Identity Linking beans (existing module, newly auto-configured) ---

    @Bean
    @ConditionalOnMissingBean
    public IdentityLinkStore identityLinkStore(OAuthProperties properties,
                                                ObjectProvider<TenantGuard> tenantGuard) {
        Path storePath = Path.of(properties.stateDir(), "identity-links.json");
        return new IdentityLinkStore(storePath, tenantGuard.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public IdentityLinkService identityLinkService(IdentityLinkStore store) {
        return new IdentityLinkService(store);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdentityResolver identityResolver(IdentityLinkStore store) {
        return new IdentityResolver(store);
    }
}
