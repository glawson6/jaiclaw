package io.jaiclaw.autoconfigure;

import io.jaiclaw.autoconfigure.secrets.SecretsConfig;
import io.jaiclaw.core.secrets.SecretsResolver;
import io.jaiclaw.core.secrets.TenantSecretsResolver;
import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Exposes JaiClaw's {@link SecretsResolver} and {@link
 * TenantSecretsResolver} as Spring beans for direct injection.
 *
 * <p>The matching {@code PropertySource} bridge is installed earlier
 * by {@code SecretsEnvironmentPostProcessor} (via {@code
 * META-INF/spring.factories}) so {@code ${VAR}} placeholders in
 * {@code application.yml} resolve through the same chain. This
 * auto-config gives application code a typed handle on the same
 * resolver for use cases where direct lookup is preferred (e.g., a
 * service that needs to read a secret at request time with the
 * current {@link TenantGuard}).
 *
 * <p>Gated on {@code jaiclaw.secrets.provider} being set; if unset,
 * no beans are registered and behavior is identical to today's
 * pure-{@code ${VAR}} resolution.
 *
 * <p>0.9.2 secrets baseline.
 */
@AutoConfiguration(after = JaiClawTenantAutoConfiguration.class)
@ConditionalOnProperty(name = "jaiclaw.secrets.provider")
public class JaiClawSecretsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawSecretsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SecretsResolver jaiClawSecretsResolver(Environment env) {
        SecretsResolver resolver = SecretsConfig.build(env);
        if (resolver == null) {
            throw new IllegalStateException(
                    "jaiclaw.secrets.provider is set but no providers were configured. "
                    + "Check jaiclaw.secrets.chain and the per-provider properties "
                    + "(jaiclaw.secrets.file.path, jaiclaw.secrets.onepassword.vault, ...)");
        }
        log.debug("Exposed SecretsResolver bean with providers={}",
                resolver.chain().stream().map(p -> p.name()).toList());
        return resolver;
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantSecretsResolver jaiClawTenantSecretsResolver(
            SecretsResolver resolver, TenantGuard guard) {
        return new TenantSecretsResolver(resolver, guard);
    }
}
