package io.jaiclaw.examples.supporttriage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Support triage pipeline example.
 *
 * <p>6-stage pipeline mixing AGENT and PROCESSOR stages to classify, look up
 * customer context, retrieve canned KB answers, draft a resolution, and either
 * close the ticket or escalate it. Designed to be driven from a Spring Shell —
 * see {@link SupportTriageShellCommands}.
 *
 * <p>Run:
 * <pre>
 *   ANTHROPIC_API_KEY=sk-… java -jar jaiclaw-example-support-triage-pipeline-*.jar
 * </pre>
 */
@SpringBootApplication
public class SupportTriageApplication {
    public static void main(String[] args) {
        SpringApplication.run(SupportTriageApplication.class, args);
    }
}
