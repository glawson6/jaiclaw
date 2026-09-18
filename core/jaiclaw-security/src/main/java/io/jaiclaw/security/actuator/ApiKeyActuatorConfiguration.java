package io.jaiclaw.security.actuator;

import io.jaiclaw.security.ApiKeyStore;
import io.jaiclaw.security.JaiClawSecurityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link ApiKeyActuatorEndpoint} when Actuator is on the classpath.
 *
 * <p>Gated by class <em>name</em> so Actuator stays an optional dependency of
 * {@code jaiclaw-security}. Opt out with
 * {@code jaiclaw.security.actuator.enabled=false}.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.security.JaiClawSecurityAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@ConditionalOnProperty(prefix = "jaiclaw.security.actuator", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ApiKeyActuatorConfiguration {

    @Bean
    @ConditionalOnBean(ApiKeyStore.class)
    @ConditionalOnMissingBean(ApiKeyActuatorEndpoint.class)
    public ApiKeyActuatorEndpoint apiKeyActuatorEndpoint(JaiClawSecurityProperties properties,
                                                         ApiKeyStore store) {
        return new ApiKeyActuatorEndpoint(properties, store);
    }
}
