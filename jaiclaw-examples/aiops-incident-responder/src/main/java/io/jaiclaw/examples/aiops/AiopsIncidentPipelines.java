package io.jaiclaw.examples.aiops;

import io.jaiclaw.pipeline.dsl.JaiClawPipeline;
import org.springframework.context.annotation.Configuration;

/**
 * AIOps incident-responder pipeline definition.
 *
 * <p>Trigger is a Camel direct URI ({@code direct:incident-alert}); the Phase E
 * convergence route also exposes {@code direct:pipeline-<id>} so the
 * Spring Shell's {@code incident} command (which delegates to
 * {@code PipelineGateway}) hits the same code path.
 */
@Configuration
public class AiopsIncidentPipelines extends JaiClawPipeline {

    static final String PIPELINE_ID = "aiops-incident";

    private static final String RUNBOOK_LIBRARY = """
            Embedded runbook library (5 entries):
              - api_5xx_spike  → check recent deploy, restart service-api, monitor 5 min
              - oom_killed     → scale up replicas to 4, then investigate memory leak
              - db_slow_query  → identify long-running query, clear redis-main cache, then alert DBA
              - queue_backlog  → scale up consumers, then page on-call if backlog grows
              - tls_cert_warn  → no auto-action; page primary on-call
            """;

    @Override
    public void define() {
        pipeline(PIPELINE_ID)
                .name("AIOps Incident Responder")
                .description("Triage, root-cause, runbook lookup, auto-remediate, escalate, post-mortem.")
                .trigger().camelUri("direct:incident-alert")
                .then("triage").agent("default")
                    .systemPrompt("""
                        You are an on-call SRE triaging an alert. Reply with exactly:
                          severity: <P1|P2|P3|P4>
                          system: <one short component name>
                          blast_radius: <one short sentence>

                        Alert:
                        {{input}}
                        """)
                .then("root-cause").agent("default")
                    .systemPrompt("""
                        You are correlating the alert with recent activity. Make up plausible details if needed
                        (it's a demo). Reply with exactly:
                          probable_cause: <one short sentence>
                          recent_changes: <comma-separated list>

                        Triage:
                        {{stages.triage.output}}

                        Alert:
                        {{input}}
                        """)
                .then("runbook-lookup").agent("default")
                    .systemPrompt(("""
                        Choose the most relevant runbook from this library and emit the action verbs verbatim
                        so the downstream PROCESSOR can parse them (look for keywords like "restart", "cache",
                        "scale", "page on-call").

                        """ + RUNBOOK_LIBRARY + """

                        Reply with exactly:
                          runbook: <library key>
                          action: <one of the action verbs above>

                        Root cause:
                        {{stages.root-cause.output}}
                        """))
                .then("auto-remediate").processor("remediationBean")
                .then("escalate-or-resolve").processor("escalationBean")
                .then("post-mortem-draft").agent("default")
                    .systemPrompt("""
                        Draft a 5-Whys post-mortem (5 short whys + one conclusion). Use the full upstream context.

                        Triage:        {{stages.triage.output}}
                        Root cause:    {{stages.root-cause.output}}
                        Runbook:       {{stages.runbook-lookup.output}}
                        Remediation:   {{stages.auto-remediate.output}}
                        Escalation:    {{stages.escalate-or-resolve.output}}
                        """)
                .output()
                    .log()
                    .template("""
                            === INCIDENT EXECUTION {{pipeline.executionId}} ===
                            ALERT: {{input}}

                            ESCALATION DECISION:
                            {{stages.escalate-or-resolve.output}}

                            POST-MORTEM:
                            {{stages.post-mortem-draft.output}}
                            """);
    }
}
