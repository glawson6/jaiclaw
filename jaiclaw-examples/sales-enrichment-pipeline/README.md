# Sales Enrichment Pipeline

Nightly batch enrichment + first-touch outreach drafting for new CRM leads.
Showcases the **CRON trigger** (via Camel quartz) combined with a per-tick
**batch-loop pattern**: every cron firing pops one lead off an in-memory queue.

## Problem

SDRs and BDRs spend 60–70% of their time on manual prospect research and
personalisation. AI-driven SDR pipelines deliver 4.2× ROI with 76% higher
win rates and 78% shorter deal cycles. The pattern lands first in ops teams
the day after a conference or list import.

## Solution

A five-stage pipeline triggered by `0 0 2 * * ?` (nightly 02:00). Each tick
pops a single lead off `CrmLeadRepository`'s queue:

1. **load-new-leads** (PROCESSOR) — pops one lead; emits `NO_LEADS` if empty
2. **enrich** (AGENT, tools=full) — recent funding, headcount, tech stack
3. **score** (AGENT) — ICP fit 1-10 with rationale
4. **draft-outreach** (AGENT) — three-sentence personalised email
5. **write-back** (PROCESSOR) — append to JSONL + repository's enriched list

## Architecture

```
quartz: nightly 02:00      OR    shell `run-now`
            │                            │
            └──────────────┬─────────────┘
                           ▼
              ┌──────────────────────────────────────────────┐
              │ load-new-leads (PROCESSOR, pops 1)           │
              │   ↓                                          │
              │ enrich → score → draft-outreach (AGENT × 3)  │
              │   ↓                                          │
              │ write-back (PROCESSOR, JSONL + repo)         │
              └──────────────────────────────────────────────┘
                           │
                           ▼
            ~/.jaiclaw/sales-enrichment/enriched.jsonl
```

## Design

- **CRON via quartz.** Same plumbing as the competitive-intel example.
- **One execution per lead.** The CRON tick (or each `run-now`) handles exactly
  one lead. This keeps each `executionId` traceable in the tracker and the
  output, and matches how production SDR teams want failures to land
  (a single lead failing should not poison the batch).
- **Tools profile `full`.** The `enrich` agent has `WebSearchTool`, which
  hits DuckDuckGo by default — no API key needed. Drop to `minimal` if the
  example must run offline.
- **Stub CRM that's actually useful.** `CrmLeadRepository` is a tiny
  `ConcurrentLinkedDeque<Lead>` seeded with five plausible leads. Add more
  via the shell's `add-lead` command.

## Prerequisites

- Java 21
- `ANTHROPIC_API_KEY`
- Outbound network (for `WebSearchTool`; or set `tools.profile: minimal`)

## Build & Run

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw package -pl :jaiclaw-example-sales-enrichment-pipeline -am -DskipTests -o
ANTHROPIC_API_KEY=sk-ant-... \
  java -jar jaiclaw-examples/sales-enrichment-pipeline/target/jaiclaw-example-sales-enrichment-pipeline-*.jar
```

Inside the shell:

```
shell:> queue
queueSize=5

shell:> add-lead "Talia Brooks" "Cobalt Health"
Queued lead (Talia Brooks, Cobalt Health). Queue size = 6

shell:> run-now
Submitted executionId=… Queue size after submit = 5. Run `executions` …

shell:> executions
…                                     SUCCESS    7421

shell:> list-enriched
{"enrichedAt":"…","record":"name: Aisha Sharma  \\n company: Nimbus Logistics  \\n recent_funding: …"}
```

## Extension points

- **Real CRM:** swap `CrmLeadRepository` for a JDBC / REST bean against
  Salesforce / HubSpot. The shape ({@code popNext()} + {@code recordEnriched(...)})
  stays the same.
- **Vertical-specific enrichment:** chain extra AGENT or PROCESSOR stages
  (industry classifier, intent score, deal-size estimate) before write-back.
- **Real outbound delivery:** add a final stage that hands the draft to
  Outreach.io / SalesLoft via their REST APIs.
- **Multi-tenant lead sharding:** wire a tenant context resolver and key the
  `CrmLeadRepository` by tenant ID so each squad gets its own queue.
