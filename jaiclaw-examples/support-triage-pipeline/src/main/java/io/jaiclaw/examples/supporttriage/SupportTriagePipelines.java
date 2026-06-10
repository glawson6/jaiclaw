package io.jaiclaw.examples.supporttriage;

import io.jaiclaw.pipeline.dsl.JaiClawPipeline;
import org.springframework.context.annotation.Configuration;

/**
 * Support-triage pipeline definition.
 *
 * <p>Pipeline ID {@code support-triage}. Mixes AGENT and PROCESSOR stages:
 *
 * <ol>
 *   <li>{@code classify-and-sentiment} (AGENT) — intent category + urgency + tone</li>
 *   <li>{@code context-fetch} (PROCESSOR) — looks up customer record via {@code crmLookup}</li>
 *   <li>{@code knowledge-retrieval} (AGENT) — searches the internal KB for a canned answer</li>
 *   <li>{@code resolve-or-draft} (AGENT) — drafts a resolution; emits {@code confidence: X.Y}</li>
 *   <li>{@code escalation-gate} (PROCESSOR) — routes to human if confidence < 0.7 or VIP</li>
 *   <li>{@code close-and-log} (PROCESSOR) — appends a case-notes line for the audit trail</li>
 * </ol>
 */
@Configuration
public class SupportTriagePipelines extends JaiClawPipeline {

    static final String PIPELINE_ID = "support-triage";

    @Override
    public void define() {
        pipeline(PIPELINE_ID)
                .name("Support Triage Pipeline")
                .description("Classify, look up customer context, draft a resolution, and escalate if needed.")
                .trigger().manual()
                .then("classify-and-sentiment").agent("default")
                    .systemPrompt("""
                        You are a Tier-1 support classifier. Read the customer message and reply with exactly these lines:
                          intent: <billing|technical|account|other>
                          urgency: <low|medium|high>
                          sentiment: <positive|neutral|negative|angry>

                        The message is:
                        {{input}}
                        """)
                .then("context-fetch").processor("crmLookup")
                .then("knowledge-retrieval").agent("default")
                    .systemPrompt("""
                        You are a knowledge-base assistant. Using the classified ticket and customer context below,
                        retrieve the most relevant KB snippet from the canned answers you have memorised
                        (billing → "Refunds within 30 days", technical → "Try restart then clear cache",
                        account → "Password reset via email link", other → "Forwarding to specialist").

                        Reply with exactly one line:
                          kb_snippet: <text>

                        Classification + context:
                        {{stages.classify-and-sentiment.output}}
                        {{stages.context-fetch.output}}
                        """)
                .then("resolve-or-draft").agent("default")
                    .systemPrompt("""
                        You are a Tier-1 support agent. Using the upstream classification, customer context,
                        and KB snippet, draft a short customer-facing reply. End with a confidence marker.

                        Reply format:
                          response: <one short paragraph addressed to the customer>
                          confidence: <decimal between 0.0 and 1.0>

                        Upstream:
                        {{stages.classify-and-sentiment.output}}
                        {{stages.context-fetch.output}}
                        {{stages.knowledge-retrieval.output}}
                        """)
                .then("escalation-gate").processor("escalationGate")
                .then("close-and-log").processor("closeAndLog")
                .output()
                    .log()
                    .template("=== SUPPORT TRIAGE RESULT (executionId={{pipeline.executionId}}) ===\n"
                            + "{{stages.close-and-log.output}}");
    }
}
