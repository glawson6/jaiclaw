package io.jaiclaw.rules.config;

import io.jaiclaw.rules.engine.config.DroolsConfig;
import io.jaiclaw.rules.engine.config.DroolsProperties;
import io.jaiclaw.rules.engine.loader.RuleLoaderFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Test configuration for Spring Boot integration tests.
 * Creates the beans that would normally be created by JaiClawRulesAutoConfiguration.
 */
@Configuration
@EnableConfigurationProperties(DroolsProperties.class)
public class TestConfig {

    @Bean
    public RuleLoaderFactory ruleLoaderFactory() {
        return new RuleLoaderFactory();
    }

    @Bean
    public DroolsConfig droolsConfig(DroolsProperties droolsProperties, RuleLoaderFactory ruleLoaderFactory) {
        return new DroolsConfig(droolsProperties, ruleLoaderFactory);
    }
}
