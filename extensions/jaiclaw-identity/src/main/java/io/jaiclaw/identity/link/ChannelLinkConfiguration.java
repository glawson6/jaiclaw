package io.jaiclaw.identity.link;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.identity.IdentityLinkStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Clock;

/**
 * Wires the channel-linking ceremony.
 *
 * <p>Opt-in via {@code jaiclaw.identity.link.enabled=true}. Provider-neutral —
 * endpoints and credentials are configuration, and the flow is plain OAuth.
 *
 * <p>Registered by {@code JaiClawIdentityAutoConfiguration} via {@code @Import},
 * matching how the module's other beans are wired.
 *
 * <p>1.4.0.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
@ConditionalOnProperty(prefix = "jaiclaw.identity.link", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ChannelLinkProperties.class)
public class ChannelLinkConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChannelLinkConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ChannelLinkNonceStore.class)
    public ChannelLinkNonceStore channelLinkNonceStore(
            ObjectProvider<TenantGuard> tenantGuard) {
        log.info("Channel linking using the in-memory nonce store. Deployments running more "
                + "than one gateway replica must supply a shared ChannelLinkNonceStore bean — "
                + "a callback landing on another pod would otherwise fail to find its nonce.");
        return new InMemoryChannelLinkNonceStore(
                tenantGuard.getIfAvailable(() -> new TenantGuard(TenantProperties.DEFAULT)),
                Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(ChannelLinkService.TokenExchanger.class)
    public ChannelLinkService.TokenExchanger channelLinkTokenExchanger(
            ChannelLinkProperties properties, Environment environment) {
        // Defaults to the OIDC tenant claim so the two cannot drift apart.
        String tenantClaim = environment.getProperty(
                "jaiclaw.security.oidc.tenant-claim", "organization_id");
        return new HttpTokenExchanger(properties, tenantClaim);
    }

    @Bean
    @ConditionalOnMissingBean(ChannelLinkService.class)
    public ChannelLinkService channelLinkService(
            ChannelLinkProperties properties,
            ChannelLinkNonceStore nonceStore,
            IdentityLinkStore linkStore,
            ChannelLinkService.TokenExchanger tokenExchanger,
            ObjectProvider<TenantGuard> tenantGuard) {

        if (!properties.isUsable()) {
            throw new IllegalStateException(
                    "jaiclaw.identity.link.enabled=true but the flow is incompletely "
                            + "configured. Required: authorize-uri, token-uri, client-id, "
                            + "redirect-uri.");
        }
        log.info("Channel linking enabled: authorize={}, redirect={}, nonce TTL={}",
                properties.authorizeUri(), properties.redirectUri(), properties.nonceTtl());

        return new ChannelLinkService(properties, nonceStore, linkStore, tokenExchanger,
                tenantGuard.getIfAvailable(() -> new TenantGuard(TenantProperties.DEFAULT)),
                Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(ChannelLinkController.class)
    public ChannelLinkController channelLinkController(ChannelLinkService linkService) {
        return new ChannelLinkController(linkService);
    }
}
