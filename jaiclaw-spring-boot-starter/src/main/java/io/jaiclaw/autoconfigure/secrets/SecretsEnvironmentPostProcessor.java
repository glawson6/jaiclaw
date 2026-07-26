package io.jaiclaw.autoconfigure.secrets;

import io.jaiclaw.core.secrets.SecretsResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Map;

/**
 * Bridges JaiClaw's {@link SecretsResolver} into Spring's
 * {@code Environment} as a high-precedence {@link
 * org.springframework.core.env.PropertySource}, so existing
 * {@code ${VAR}} placeholders in {@code application.yml} resolve
 * through the configured provider chain.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} + 10 — high enough to
 * intercept config-file placeholder resolution, low enough that
 * explicit JVM {@code -D} system properties and command-line args
 * still win.
 *
 * <p>Registered via {@code META-INF/spring.factories}. Gated on the
 * {@code jaiclaw.secrets.provider} property being set; absent the
 * property, this post-processor is a no-op and behavior is identical
 * to today's pure-{@code ${VAR}} resolution.
 *
 * <p>0.9.2 secrets baseline.
 */
public final class SecretsEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SecretsEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        SecretsResolver resolver = SecretsConfig.build(environment, SecretsConfig.defaultProviderFactory());
        if (resolver == null) {
            return;
        }
        Map<String, String> snapshot = SecretsConfig.debugSnapshot(environment);
        log.info("Installing JaiClaw secrets PropertySource: providers={}, config={}",
                resolver.chain().stream().map(p -> p.name()).toList(),
                snapshot);
        // Add at the FIRST position so we intercept placeholder
        // resolution before application.yml's own values are read,
        // but JVM -D and command-line args still win because Spring
        // Boot's SystemEnvironmentPropertySource and CommandLine
        // PropertySource sit even higher.
        environment.getPropertySources().addFirst(new SecretsPropertySource(resolver));
    }

    @Override
    public int getOrder() {
        // Run very early but allow other post-processors to set
        // jaiclaw.secrets.* via active profile resolution first.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
