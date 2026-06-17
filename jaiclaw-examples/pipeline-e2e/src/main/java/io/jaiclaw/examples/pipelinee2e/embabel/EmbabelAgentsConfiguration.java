package io.jaiclaw.examples.pipelinee2e.embabel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Embabel {@code @Agent} beans used by the
 * {@code embabel-pipe} (pure-compute) and {@code embabel-triage-pipe}
 * (LLM-backed) e2e pipelines.
 *
 * <p>The outer guard ({@code @ConditionalOnClass("com.embabel.agent.api.annotation.Agent")})
 * mirrors the established pattern in
 * {@code SecurityToolsAutoConfiguration.EmbabelAgentConfiguration}: the
 * agents only land in the context when Embabel is actually on the
 * classpath. For pipeline-e2e that's always true (the pom pulls
 * {@code jaiclaw-starter-embabel}), but the conditional keeps the module
 * loadable in any reactor configuration that excludes Embabel.
 *
 * <p>{@link TicketTriageAgent} is registered only when
 * {@code ANTHROPIC_API_KEY} is set in the environment, so the
 * default e2e run that exercises only the pure-compute scoring agent
 * stays free of LLM dependencies. The check happens at bean-factory
 * evaluation time, not at request time, which matches the env-var
 * gating already used by {@code E2ePipelines} for the {@code agent-pipe}.
 */
@Configuration
@ConditionalOnClass(name = "com.embabel.agent.api.annotation.Agent")
public class EmbabelAgentsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EmbabelAgentsConfiguration.class);

    @Bean
    public TicketScoringAgent ticketScoringAgent() {
        log.info("Registering Embabel agent: TicketScoringAgent (pure-compute)");
        return new TicketScoringAgent();
    }

    @Bean
    @org.springframework.context.annotation.Conditional(AnthropicKeyPresent.class)
    public TicketTriageAgent ticketTriageAgent() {
        log.info("Registering Embabel agent: TicketTriageAgent (LLM-backed; ANTHROPIC_API_KEY detected)");
        return new TicketTriageAgent();
    }

    /**
     * Spring {@link org.springframework.context.annotation.Condition} that
     * matches when {@code ANTHROPIC_API_KEY} is set and non-blank. Lets us
     * gate {@link TicketTriageAgent} at bean-factory time without forcing a
     * profile or pulling in extra config plumbing.
     */
    static class AnthropicKeyPresent implements org.springframework.context.annotation.Condition {
        @Override
        public boolean matches(
                org.springframework.context.annotation.ConditionContext context,
                org.springframework.core.type.AnnotatedTypeMetadata metadata) {
            String key = System.getenv("ANTHROPIC_API_KEY");
            return key != null && !key.isBlank();
        }
    }
}
