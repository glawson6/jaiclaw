package io.jaiclaw.examples.salesenrich;

import io.jaiclaw.pipeline.dsl.JaiClawPipeline;
import org.springframework.context.annotation.Configuration;

/**
 * Sales-enrichment pipeline. Nightly cron pops one lead at a time and runs it
 * through enrich → score → outreach drafting → CRM write-back.
 */
@Configuration
public class SalesEnrichmentPipelines extends JaiClawPipeline {

    static final String PIPELINE_ID = "sales-enrichment";

    @Override
    public void define() {
        pipeline(PIPELINE_ID)
                .name("Sales Enrichment Pipeline")
                .description("Nightly: pop a lead, enrich with web search, score ICP fit, draft outreach, write back.")
                .trigger().cron("0 0 2 * * ?")
                .then("load-new-leads").processor("loadNewLeads")
                .then("enrich").agent("default")
                    .systemPrompt("""
                        You are a sales-development analyst. Given the lead below, hypothesise plausible context:
                        recent funding, headcount trend, tech stack, product news. If a web-search tool is
                        available use it; otherwise reasonably fabricate for the demo.

                        Reply with exactly:
                          recent_funding: <short note or "unknown">
                          headcount_trend: <growing|stable|shrinking|unknown>
                          tech_stack: <comma-separated list>
                          product_news: <short note>

                        Lead:
                        {{stages.load-new-leads.output}}
                        """)
                .then("score").agent("default")
                    .systemPrompt("""
                        Score the lead 1-10 for ICP fit. Reply with exactly:
                          icp_score: <integer 1-10>
                          rationale: <one short sentence>

                        Context:
                        {{stages.load-new-leads.output}}
                        {{stages.enrich.output}}
                        """)
                .then("draft-outreach").agent("default")
                    .systemPrompt("""
                        Draft a 3-sentence personalised first-touch email. Use exactly:
                          subject: <one short line>
                          body: <three sentences, each on its own line>

                        Lead + score + enrichment:
                        {{stages.load-new-leads.output}}
                        {{stages.enrich.output}}
                        {{stages.score.output}}
                        """)
                .then("write-back").processor("crmWriteBack")
                .output()
                    .log()
                    .template("""
                            === SALES ENRICHMENT (executionId={{pipeline.executionId}}) ===
                            {{stages.write-back.output}}
                            """);
    }
}
