package io.jaiclaw.agentmind.tendencies.honcho;

import io.jaiclaw.agentmind.tendencies.learning.TendenciesLearningProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-in autoconfig for the Honcho sub-module. Activates when ALL of:
 *
 * <ul>
 *   <li>{@link HonchoClient} is on the classpath (this module's contract;
 *       always true when the sub-module is included)</li>
 *   <li>{@code jaiclaw.agentmind.tendencies.provider=honcho}</li>
 *   <li>Both the pillar and Tendencies are enabled — implied by the
 *       {@code TendenciesLearningProvider} consumers being wired</li>
 * </ul>
 *
 * <p>Consumers wire a real HTTP-backed {@link HonchoClient} bean
 * (typically built on Spring {@code WebClient} pointing at their Honcho
 * server). When no client bean is provided, the autoconfig falls back to
 * {@link NoOpHonchoClient} so the AgentMind demo runs without a live
 * Honcho server.
 *
 * <p>Plan §8 task 4.1.
 */
@AutoConfiguration
@Configuration
@ConditionalOnClass(HonchoClient.class)
@ConditionalOnProperty(prefix = "jaiclaw.agentmind.tendencies", name = "provider", havingValue = "honcho")
public class HonchoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HonchoClient honchoClient() {
        return new NoOpHonchoClient();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(HonchoClient.class)
    public TendenciesLearningProvider honchoLearningProvider(HonchoClient client) {
        return new HonchoRemoteTendenciesProvider(client);
    }
}
