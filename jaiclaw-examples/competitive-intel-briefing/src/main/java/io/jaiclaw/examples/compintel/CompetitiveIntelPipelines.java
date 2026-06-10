package io.jaiclaw.examples.compintel;

import io.jaiclaw.pipeline.dsl.JaiClawPipeline;
import org.springframework.context.annotation.Configuration;

/**
 * Competitive-intel briefing pipeline. CRON trigger (weekdays at 07:00) +
 * embedded signal cache + LLM synthesis + markdown briefing on disk.
 */
@Configuration
public class CompetitiveIntelPipelines extends JaiClawPipeline {

    static final String PIPELINE_ID = "competitive-intel-briefing";

    @Override
    public void define() {
        pipeline(PIPELINE_ID)
                .name("Competitive Intel Briefing")
                .description("Daily CI digest: collect signals, diff vs cache, synthesize, impact analysis, format.")
                // 7am every weekday. Set jaiclaw.pipeline.pipelines[…] overrides
                // if you want a different schedule per environment.
                .trigger().cron("0 0 7 ? * MON-FRI")
                .then("collect-signals").agent("default")
                    .systemPrompt("""
                        You are a competitive-intel analyst. For each competitor in the configured list, hypothesise
                        recent signals (news, pricing, hiring, product updates) — invent plausible details for the demo.

                        Reply with one block per competitor, each headed by `## <name>` and at most 4 bullets.
                        """)
                .then("detect-changes").processor("signalCacheBean")
                .then("synthesize").agent("default")
                    .systemPrompt("""
                        You are summarising what changed this week. Given the prior + current signals below,
                        produce 2-4 short bullets per competitor titled "What changed:". If a competitor has no
                        delta, write "(no notable change)".

                        {{stages.detect-changes.output}}
                        """)
                .then("impact-analysis").agent("default")
                    .systemPrompt("""
                        For each competitor's changes, write 1-2 bullets describing implications for our roadmap,
                        positioning, or pricing, and suggest one concrete response.

                        Changes:
                        {{stages.synthesize.output}}
                        """)
                .then("format-briefing").processor("briefingFormatter")
                .output()
                    .log()
                    .template("""
                            === COMPETITIVE INTEL BRIEFING (executionId={{pipeline.executionId}}) ===
                            {{stages.format-briefing.output}}
                            """);
    }
}
