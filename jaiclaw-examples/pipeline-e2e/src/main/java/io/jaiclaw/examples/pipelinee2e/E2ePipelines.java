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
 */
@Configuration
public class E2ePipelines extends JaiClawPipeline {

    static final String WITH_AGENT_ENV = "JAICLAW_E2E_WITH_AGENT";

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
    }

    private static boolean isAgentEnabled() {
        String v = System.getenv(WITH_AGENT_ENV);
        return v != null && (v.equalsIgnoreCase("true") || v.equals("1"));
    }
}
