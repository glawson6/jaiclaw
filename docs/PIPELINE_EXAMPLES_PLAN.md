# JaiClaw Pipeline Examples — Implementation Plan

Six commercially viable pipeline examples covering distinct industries and pipeline patterns.
Each is designed to showcase different trigger types, stage compositions, and output channels
while targeting real enterprise use cases with proven ROI.

---

## Selection Rationale

| # | Example | Industry | Trigger | Stages | Output | ROI Profile |
|---|---------|----------|---------|--------|--------|-------------|
| 1 | `invoice-processor` | Finance / AP | FILE / EMAIL | 5 | ERP + Slack | 200–300%, 9–12 mo |
| 2 | `support-triage-pipeline` | SaaS / E-commerce | HTTP | 6 | Ticket system + escalation | 3.5x–8x, 2–8 wk |
| 3 | `sales-enrichment-pipeline` | B2B Sales | CRON + CRM webhook | 5 | CRM write + email sequence | 4.2x, 6–9 mo |
| 4 | `contract-reviewer` | Legal / Enterprise | FILE / EMAIL | 6 | Redlined doc + CLM + calendar | 5x+, 6–12 mo |
| 5 | `aiops-incident-responder` | IT / DevOps | CAMEL_URI (webhook) | 6 | Slack escalation + Jira + runbook | 50–70% MTTR reduction |
| 6 | `competitive-intel-briefing` | Product / Strategy | CRON | 5 | Slack digest + Confluence page | Fast, 1–3 mo |

---

## Example 1 — `invoice-processor`

### Business Problem
Accounts payable teams spend 5–15 minutes per invoice on manual data entry, GL coding,
and routing. At 500 invoices/month that is 40–125 hours of labor — plus late-payment
penalties when invoices get lost in email. AI document pipelines deliver 200–300% ROI
within 12 months and are the highest-volume AI automation purchase across finance, insurance,
and legal.

### Pipeline Flow

```
[FILE/EMAIL trigger: invoice arrives]
        │
        ▼
[Stage 1: classify]          AGENT — determine document type (invoice, PO, credit memo, receipt)
        │
        ▼
[Stage 2: extract]           AGENT — extract vendor, amount, line items, PO number, due date, GL code
        │
        ▼
[Stage 3: validate]          PROCESSOR — cross-reference PO number against ERP stub; flag mismatches
        │
        ▼
[Stage 4: approve-or-flag]   AGENT — auto-approve if under threshold + matched PO; else flag for review
        │
        ▼
[Stage 5: notify]            PROCESSOR — write approval record; send Slack summary
        │
        ▼
[Output: LOG + Slack channel]
```

### Key Design Points
- Uses `FILE` trigger watching a local `/inbox` directory (easy local demo, no cloud required)
- `StageType.AGENT` for classify and extract; `StageType.PROCESSOR` (Spring bean) for rule-based validate and write
- `errorStrategy: DEAD_LETTER` routes unparseable documents to a review queue instead of crashing
- Demonstrates `{{stages.extract.output}}` template chaining between stages
- Stub ERP bean swappable for real SAP/NetSuite API bean via Spring `@Profile`

### Module Name
`jaiclaw-example-invoice-processor`

### New Files
- `InvoiceProcessorApplication.java`
- `InvoiceProcessorPipelines.java` (extends `JaiClawPipeline`)
- `InvoiceValidatorBean.java` (Spring bean, `Function<String, String>`)
- `InvoiceNotifierBean.java`
- `application.yml`
- `README.md`

---

## Example 2 — `support-triage-pipeline`

### Business Problem
Tier-0/1 support automation is the most common first AI purchase across all verticals.
Klarna's deployment replaced 853 FTEs and saved $60M. Average ROI is $3.50 per $1 invested;
top performers hit 8x. Resolution time drops from 11 minutes to under 2. This example
targets the HTTP trigger (inbound chat/ticket API) pattern that most SaaS companies need.

### Pipeline Flow

```
[HTTP trigger: POST /api/support/ticket]
        │
        ▼
[Stage 1: classify-and-sentiment]  AGENT — intent category + urgency + emotional tone
        │
        ▼
[Stage 2: context-fetch]           PROCESSOR — lookup customer account & order history (stub CRM bean)
        │
        ▼
[Stage 3: knowledge-retrieval]     AGENT — RAG over internal KB / FAQ (stub vector search)
        │
        ▼
[Stage 4: resolve-or-draft]        AGENT — generate resolution response or safe action
        │
        ▼
[Stage 5: escalation-gate]         PROCESSOR — route to human if confidence < threshold or VIP customer
        │
        ▼
[Stage 6: close-and-log]           PROCESSOR — post case notes, tag disposition
        │
        ▼
[Output: HTTP response body (JSON) + LOG]
```

### Key Design Points
- `HTTP` trigger — showcases the REST ingestion path; response body carries final resolution
- Escalation gate is a `PROCESSOR` bean using a simple confidence-score threshold rule
- Demonstrates `PipelineGateway` (from UX improvements plan) as the calling pattern in the README
- KB retrieval uses a stub in-memory map as a stand-in for a real vector store
- `security.requireAuthentication: false` for easy local demo; README shows how to enable

### Module Name
`jaiclaw-example-support-triage-pipeline`

### New Files
- `SupportTriagePipelineApplication.java`
- `SupportTriagePipelines.java`
- `CrmLookupBean.java`
- `KnowledgeBaseBean.java` (stub KB with a few canned answers)
- `EscalationGateBean.java`
- `application.yml`
- `README.md`

---

## Example 3 — `sales-enrichment-pipeline`

### Business Problem
Sales development reps spend 60–70% of their time on manual prospect research and
personalization. AI SDR pipelines deliver 4.2x ROI with 76% higher win rates, 78% shorter
deal cycles, and are the fastest-growing AI spend category. This example targets the CRON
trigger pattern (nightly batch enrichment run) that ops teams deploy immediately after a
conference or list import.

### Pipeline Flow

```
[CRON trigger: nightly at 02:00]
        │
        ▼
[Stage 1: load-new-leads]          PROCESSOR — read un-enriched leads from stub CRM bean
        │
        ▼
[Stage 2: enrich]                  AGENT — web search for company news, funding, headcount, tech stack
        │
        ▼
[Stage 3: score]                   AGENT — ICP fit score (1–10) with reasoning based on enrichment
        │
        ▼
[Stage 4: draft-outreach]          AGENT — personalized first-touch email using enrichment + ICP context
        │
        ▼
[Stage 5: write-back]              PROCESSOR — update CRM stub with score, enrichment notes, email draft
        │
        ▼
[Output: LOG summary + Slack message "X leads enriched tonight"]
```

### Key Design Points
- `CRON` trigger with configurable cron expression (`jaiclaw.pipeline.pipelines[0].trigger.expression`)
- Web search tool (`WebSearchTool`) used inside the enrich agent — no external API key needed for stub
- Demonstrates iterating over a list: the pipeline processes leads one at a time via `PipelineGateway.trigger()` loop in `ApplicationRunner`
- Shows CRON + manual trigger hybrid: the CRON fires the batch, but the README also shows how to run manually via HTTP
- Stub CRM bean returns 5 seed leads at startup; users can add more via a Spring Shell command

### Module Name
`jaiclaw-example-sales-enrichment-pipeline`

### New Files
- `SalesEnrichmentApplication.java`
- `SalesEnrichmentPipelines.java`
- `CrmLeadRepository.java` (in-memory stub, pre-seeded)
- `CrmWriteBackBean.java`
- `SalesShellCommands.java` (add leads, run batch manually)
- `application.yml`
- `README.md`

---

## Example 4 — `contract-reviewer`

### Business Problem
Outside counsel charges $300–600/hour for contract review. Salesforce eliminated $5M+ in
legal costs with AI contract automation. Contract review time drops from days to minutes.
The CLM market is $3.5B+ growing 13% annually. This example targets the FILE trigger and
multi-clause reasoning pattern that legal ops teams deploy first — before full CLM integration.

### Pipeline Flow

```
[FILE trigger: contract dropped in /contracts/inbox]
        │
        ▼
[Stage 1: extract-structure]   AGENT — parties, dates, payment terms, SLAs, termination, liability cap
        │
        ▼
[Stage 2: playbook-check]      AGENT — compare clauses against standard playbook (stub embedded in prompt)
        │                               flag each deviation with severity: OK / REVIEW / REJECT
        ▼
[Stage 3: risk-score]          AGENT — aggregate risk score (1–10) with per-clause breakdown
        │
        ▼
[Stage 4: redline]             AGENT — generate suggested redline language for REVIEW/REJECT clauses
        │
        ▼
[Stage 5: compliance-scan]     AGENT — check for GDPR DPA requirement, IP assignment, jurisdiction issues
        │
        ▼
[Stage 6: route]               PROCESSOR — auto-approve score ≤3; queue for counsel score 4–7; alert score ≥8
        │
        ▼
[Output: LOG full report + Slack notification with risk score + summary]
```

### Key Design Points
- `FILE` trigger watching `/contracts/inbox`; processed files moved to `/contracts/done`
- Playbook is embedded as a system prompt in the playbook-check stage — no external storage needed for demo
- `{{stages.extract-structure.output}}` and `{{stages.risk-score.output}}` chained through later stages
- Output includes a formatted Slack message: "Contract with Acme Corp — Risk: 6/10. 2 clauses need review."
- Demonstrates `errorStrategy: RETRY_THEN_FAIL` with `maxRetries: 2` for LLM transient failures
- README explains how to swap in a real CLM system (iManage, Ironclad, Docusign) via Camel endpoint

### Module Name
`jaiclaw-example-contract-reviewer`

### New Files
- `ContractReviewerApplication.java`
- `ContractReviewerPipelines.java`
- `ContractRouterBean.java`
- `application.yml`
- `README.md`

---

## Example 5 — `aiops-incident-responder`

### Business Problem
Each hour of production downtime costs enterprises $100K–$5M. AIOps reduces mean-time-to-
resolution by 50–70%. IT teams spend 60–80% of ticket time on repetitive, scriptable issues.
This is a clear DevOps/SRE budget line item and the fastest-demo use case because the trigger
is a webhook that anyone with Postman can fire in 30 seconds.

### Pipeline Flow

```
[CAMEL_URI trigger: direct:incident-alert  (or webhook POST from PagerDuty/Datadog)]
        │
        ▼
[Stage 1: triage]              AGENT — severity (P1–P4), affected system, blast radius estimate
        │
        ▼
[Stage 2: root-cause]          AGENT — correlate alert with recent deployments, error logs (stub log tool)
        │
        ▼
[Stage 3: runbook-lookup]      AGENT — retrieve matching runbook steps from embedded runbook library
        │
        ▼
[Stage 4: auto-remediate]      PROCESSOR — execute safe actions: restart stub service, clear stub cache
        │
        ▼
[Stage 5: escalate-or-resolve] PROCESSOR — if remediation succeeded: close; else page on-call with summary
        │
        ▼
[Stage 6: post-mortem-draft]   AGENT — auto-draft 5-Whys incident summary using full stage context
        │
        ▼
[Output: Slack message with incident timeline + recommended next steps + post-mortem draft]
```

### Key Design Points
- `CAMEL_URI` trigger (`direct:incident-alert`) + HTTP wrapper so curl/Postman can fire it easily
- Stub log tool returns canned error log snippets seeded around a few realistic failure scenarios
- `PipelineGateway` exposed as a REST endpoint for demo purposes (`POST /api/incidents/trigger`)
- Runbook library is embedded as a string in the runbook-lookup stage system prompt (5 sample runbooks)
- Demonstrates `HMAC_SHA256` transport auth between stages (configurable via property)
- README includes a `curl` one-liner that triggers a simulated P2 incident and shows full output

### Module Name
`jaiclaw-example-aiops-incident-responder`

### New Files
- `AiopsIncidentResponderApplication.java`
- `AiopsIncidentPipelines.java`
- `RemediationBean.java` (stub: logs "restarting service X")
- `EscalationBean.java`
- `IncidentTriggerController.java` (thin REST wrapper over `PipelineGateway`)
- `application.yml`
- `README.md`

---

## Example 6 — `competitive-intel-briefing`

### Business Problem
Manual competitive analysis consumes 5–20 hours/week of senior analyst time at $100–200/hr.
AI-driven CI pipelines detect competitor moves within hours and deliver briefings to
strategy teams automatically. This is the easiest sell: fastest time-to-value (1–3 months),
no external system integration required, and directly visible to C-suite stakeholders who
receive the daily briefing. Strong land-and-expand product for consultancies.

### Pipeline Flow

```
[CRON trigger: every weekday at 07:00]
        │
        ▼
[Stage 1: collect-signals]     AGENT — web search for each tracked competitor: news, pricing page, job posts
        │
        ▼
[Stage 2: detect-changes]      PROCESSOR — diff today's signals against cached prior run (file-based cache)
        │
        ▼
[Stage 3: synthesize]          AGENT — narrative synthesis: "What changed this week and why it matters"
        │
        ▼
[Stage 4: impact-analysis]     AGENT — implications for your product/sales strategy; suggested responses
        │
        ▼
[Stage 5: format-briefing]     PROCESSOR — assemble final markdown briefing with sections per competitor
        │
        ▼
[Output: Slack digest message + LOG (Confluence write shown in README as extension)]
```

### Key Design Points
- `CRON` trigger — simplest demo; fire manually via Spring Shell for development
- Competitor list configured in `application.yml` (`jaiclaw.competitive.competitors[]: [Acme, Globex, Initech]`)
- Signal cache written to `~/.jaiclaw/competitive-intel/` as plain JSON — no database required
- Change detection (`PROCESSOR` bean) is pure Java string diff — shows how processors complement agents
- `{{stages.collect-signals.output}}` and `{{stages.detect-changes.output}}` both flow into synthesis
- Output Slack message formatted as a digest with emoji severity indicators (configurable)
- README shows extension path: add a Confluence/Notion write step via `CAMEL_URI` output

### Module Name
`jaiclaw-example-competitive-intel-briefing`

### New Files
- `CompetitiveIntelApplication.java`
- `CompetitiveIntelPipelines.java`
- `CompetitiveIntelProperties.java` (`@ConfigurationProperties`, competitor list)
- `SignalCacheBean.java` (read/write JSON cache to disk)
- `BriefingFormatterBean.java`
- `CompetitiveIntelShellCommands.java` (run now, add competitor, show last briefing)
- `application.yml`
- `README.md`

---

## Shared Implementation Notes

### Dependencies (all 6 examples)
```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-starter-pipeline</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Examples 3 and 6 (CRON) also add:
```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-starter-shell</artifactId>
</dependency>
```

### Skills Allowlist (every example must set this)
```yaml
jaiclaw:
  skills:
    allow-bundled: []
```

### AI Provider (default for all examples)
```yaml
spring:
  ai:
    anthropic:
      chat:
        options:
          model: claude-sonnet-4-6
```

### README Template Sections (required per CLAUDE.md)
1. Problem statement (2–3 sentences, business value)
2. Solution overview
3. Pipeline architecture diagram (ASCII or Mermaid)
4. Design decisions (why this trigger, why these stage types)
5. Prerequisites (API keys, env vars)
6. Build & Run instructions
7. Verification / expected output
8. Extension points (how to connect real systems)

---

## Implementation Order

| Step | Action |
|------|--------|
| 1 | Implement UX improvements from `PIPELINE_UX_IMPROVEMENTS.md` first — `PipelineGateway` and startup validation are used by multiple examples |
| 2 | `support-triage-pipeline` — simplest pipeline, HTTP trigger, fastest feedback loop |
| 3 | `invoice-processor` — FILE trigger, showcases DEAD_LETTER error strategy |
| 4 | `aiops-incident-responder` — CAMEL_URI trigger, transport auth, manual HTTP firing |
| 5 | `competitive-intel-briefing` — CRON trigger, change-detection processor pattern |
| 6 | `sales-enrichment-pipeline` — CRON + batch loop over multiple items |
| 7 | `contract-reviewer` — most complex LLM reasoning chain; do last |
| 8 | Add all 6 to `jaiclaw-examples/pom.xml` `<modules>` list |
