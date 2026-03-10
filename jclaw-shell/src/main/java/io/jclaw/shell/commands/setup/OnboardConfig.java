package io.jclaw.shell.commands.setup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
class OnboardConfig {

    @Bean
    @ConditionalOnMissingBean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
