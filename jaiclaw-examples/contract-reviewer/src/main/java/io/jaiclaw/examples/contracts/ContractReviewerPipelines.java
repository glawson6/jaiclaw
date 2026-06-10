package io.jaiclaw.examples.contracts;

import io.jaiclaw.pipeline.ErrorStrategy;
import io.jaiclaw.pipeline.dsl.JaiClawPipeline;
import org.springframework.context.annotation.Configuration;

/**
 * Contract-reviewer pipeline. FILE trigger + 6 stages + RETRY_THEN_FAIL.
 *
 * <p>The playbook is intentionally embedded in {@code playbook-check}'s system
 * prompt — the example deliberately avoids any external storage so the
 * deployment is "java -jar" only.
 */
@Configuration
public class ContractReviewerPipelines extends JaiClawPipeline {

    static final String PIPELINE_ID = "contract-reviewer";

    private static final String PLAYBOOK = """
            Standard playbook (five short rules):
              R1. Payment terms must be NET30 or shorter.
              R2. Liability cap must be ≥ 1× annual contract value.
              R3. No automatic renewal unless explicit 60-day notice clause is included.
              R4. Termination for convenience must be ≤ 90 days.
              R5. Indemnity must be mutual.
            For each rule reply OK / REVIEW / REJECT with one short justification.
            """;

    @Override
    public void define() {
        String inboxUri = "file:" + ContractReviewerBeans.INBOX.toString()
                + "?move=.done&moveFailed=.error&readLock=changed";

        pipeline(PIPELINE_ID)
                .name("Contract Reviewer")
                .description("Extract structure, playbook check, risk score, redline, compliance scan, route.")
                .errorStrategy(ErrorStrategy.RETRY_THEN_FAIL)
                .maxRetries(2)
                .trigger().file(inboxUri)
                .then("extract-structure").agent("default")
                    .systemPrompt("""
                        Extract the key structural fields from the contract. Reply with exactly:
                          parties: <comma-separated>
                          effective_date: <YYYY-MM-DD or "unspecified">
                          term: <one short sentence>
                          payment_terms: <one short sentence>
                          liability_cap: <one short sentence>
                          termination: <one short sentence>

                        Contract:
                        {{input}}
                        """)
                .then("playbook-check").agent("default")
                    .systemPrompt(PLAYBOOK + """

                        Extracted structure:
                        {{stages.extract-structure.output}}
                        """)
                .then("risk-score").agent("default")
                    .systemPrompt("""
                        Aggregate the playbook-check into a single 1-10 risk score (10 = unacceptable).
                        Reply with exactly:
                          risk_score: <integer>
                          rationale: <one short sentence per clause that drives risk>

                        Playbook check:
                        {{stages.playbook-check.output}}
                        """)
                .then("redline").agent("default")
                    .systemPrompt("""
                        For each REVIEW/REJECT item from the playbook check, propose suggested redline language.
                        Reply with one block per clause, headed by `### <clause>`, with one short paragraph each.

                        Playbook + risk:
                        {{stages.playbook-check.output}}
                        {{stages.risk-score.output}}
                        """)
                .then("compliance-scan").agent("default")
                    .systemPrompt("""
                        Scan the contract for compliance flags: GDPR DPA presence, IP assignment clarity, jurisdiction.
                        Reply with exactly three lines:
                          gdpr_dpa: <OK|MISSING|UNCLEAR>
                          ip_assignment: <OK|MISSING|UNCLEAR>
                          jurisdiction: <jurisdiction name or "missing">

                        Source:
                        {{input}}
                        """)
                .then("route").processor("contractRouter")
                .output()
                    .log()
                    .template("""
                            === CONTRACT REVIEW (executionId={{pipeline.executionId}}) ===
                            {{stages.route.output}}
                            """);
    }
}
