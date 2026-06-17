package io.jaiclaw.examples.pipelinee2e;

import io.jaiclaw.pipeline.dsl.JaiClawPipeline;
import org.springframework.context.annotation.Configuration;

/**
 * Pipeline definitions for the e2e tests.
 *
 * <p>{@code processor-pipe} — happy-path, no LLM required. Two PROCESSOR
 * stages chained with {@code .then()} (Phase C DSL alias). Output template
 * exercises {@code {{stages.X.output}}} and {@code {{input}}} (Phase B).
 *
 * <p>{@code agent-pipe} — only registered when
 * {@code JAICLAW_E2E_WITH_AGENT=true} is set in the environment, so the
 * default boot does not need an LLM provider key.
 *
 * <p>{@code embabel-pipe} — only registered when
 * {@code JAICLAW_E2E_WITH_EMBABEL=true} is set. Exercises the
 * {@code runtime: embabel} AGENT-stage path that ships in 0.9.1:
 * {@code score} routes through {@code AgentOrchestrationPort} →
 * {@code TicketScoringAgent} (pure-compute, no LLM), then a downstream
 * PROCESSOR stage uppercases the JSON to prove cross-runtime chaining
 * works.
 *
 * <p>{@code embabel-triage-pipe} — additionally registered when an LLM
 * key is present alongside {@code JAICLAW_E2E_WITH_EMBABEL=true}.
 * Exercises an LLM-backed Embabel agent through the same orchestration
 * port path.
 */
@Configuration
public class E2ePipelines extends JaiClawPipeline {

    static final String WITH_AGENT_ENV = "JAICLAW_E2E_WITH_AGENT";
    static final String WITH_EMBABEL_ENV = "JAICLAW_E2E_WITH_EMBABEL";

    @Override
    public void define() {
        // Happy-path: two Spring-bean stages, chained via .then().
        pipeline("processor-pipe")
                .name("E2E Processor Pipeline")
                .description("PROCESSOR-only pipeline exercising .then(), {{input}}, and the tracker")
                .trigger().manual()
                .then("upper").processor("upperCase")
                .then("exclaim").processor("addExclaim")
                .output().log().template("upper={{stages.upper.output}} input-was={{input}}");

        // Optional AGENT pipeline — only when explicitly enabled.
        if (isAgentEnabled()) {
            pipeline("agent-pipe")
                    .name("E2E Agent Pipeline")
                    .description("AGENT stage exercising {{input}} with a real LLM")
                    .trigger().manual()
                    .then("answer").agent("default")
                        .systemPrompt("You are a test assistant. Reply with exactly: E2E_AGENT_OK. The original input was: {{input}}")
                    .output().log().template("agent={{stages.answer.output}}");
        }

        // Optional EMBABEL pipeline — pure-compute, no LLM key needed.
        if (isEmbabelEnabled()) {
            pipeline("embabel-pipe")
                    .name("E2E Embabel Pipeline")
                    .description("AGENT stage with runtime=EMBABEL routed through the GOAP planner, "
                            + "chained into a PROCESSOR stage to prove cross-runtime composition")
                    .trigger().manual()
                    .then("score").embabelAgent("TicketScoringAgent")
                    .then("format").processor("upperCase")
                    .output().log().template("embabel-result={{stages.format.output}} input-was={{input}}");

            // LLM-backed embabel pipeline — requires ANTHROPIC_API_KEY in addition to the env flag.
            if (isLlmAgentEnabled()) {
                pipeline("embabel-triage-pipe")
                        .name("E2E Embabel Triage Pipeline")
                        .description("AGENT stage with runtime=EMBABEL invoking an LLM-backed @Agent")
                        .trigger().manual()
                        .then("triage").embabelAgent("TicketTriageAgent")
                        .output().log().template("triage={{stages.triage.output}}");
            }
        }
    }

    private static boolean isAgentEnabled() {
        return isEnvFlagSet(WITH_AGENT_ENV);
    }

    private static boolean isEmbabelEnabled() {
        return isEnvFlagSet(WITH_EMBABEL_ENV);
    }

    private static boolean isLlmAgentEnabled() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        return key != null && !key.isBlank() && !"not-set".equals(key);
    }

    private static boolean isEnvFlagSet(String name) {
        String v = System.getenv(name);
        return v != null && (v.equalsIgnoreCase("true") || v.equals("1"));
    }
}
