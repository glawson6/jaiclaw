package io.jaiclaw.examples.pipelinee2e;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application used by the JaiClaw pipeline e2e tests.
 *
 * <p>By default boots a PROCESSOR-only pipeline ({@code processor-pipe}) that
 * needs no LLM key. Setting {@code JAICLAW_E2E_WITH_AGENT=true} additionally
 * registers an AGENT-stage pipeline ({@code agent-pipe}).
 *
 * <p>Run with {@code --spring.profiles.active=broken} to load
 * {@code application-broken.yml} — the validator should reject startup with a
 * consolidated error message.
 */
@SpringBootApplication
public class PipelineE2eApplication {
    public static void main(String[] args) {
        SpringApplication.run(PipelineE2eApplication.class, args);
    }
}
