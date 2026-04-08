package io.jaiclaw.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Sets the default banner to the JaiClaw banner at lowest priority.
 * Any application can override by setting {@code spring.banner.location}
 * in its own {@code application.yml}.
 */
public class JaiClawBannerEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.containsProperty("spring.banner.location")) {
            environment.getPropertySources().addLast(
                    new MapPropertySource("jaiclaw-banner-defaults",
                            Map.of("spring.banner.location", "classpath:jaiclaw-banner.txt")));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
