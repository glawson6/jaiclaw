# JaiClaw Examples

Example applications demonstrating JaiClaw framework capabilities. Each is a standalone Spring Boot app that can be built and run independently.

> **Note:** Each example is a self-contained Spring Boot application with its own `application.yml`. Examples do **not** inherit configuration from the gateway app (`jaiclaw-gateway-app`). If you add a new LLM provider or change settings in the gateway's `application.yml`, those changes won't apply to examples — you must update each example's `application.yml` separately.

## Overview

| # | Example | Category | Modules | Description |
|---|---------|----------|---------|-------------|
| 1 | [daily-briefing](../jaiclaw-examples/daily-briefing/) | Cron | Cron, Telegram, Email | Scheduled morning briefing with news and weather |
| 2 | [sales-report](../jaiclaw-examples/sales-report/) | Cron | Cron, Canvas | Weekly sales dashboard with HTML report |
| 3 | [price-monitor](../jaiclaw-examples/price-monitor/) | Cron | Cron, Browser, SMS | Hourly price checker with SMS alerts |
| 4 | [system-monitor](../jaiclaw-examples/system-monitor/) | Cron | Cron, Telegram | Daily server health analysis with Telegram reporting |
| 5 | [code-review-bot](../jaiclaw-examples/code-review-bot/) | Embabel | Embabel, Canvas, Plugin | GOAP-orchestrated PR code review |
| 6 | [travel-planner](../jaiclaw-examples/travel-planner/) | Embabel | Embabel, Browser, Voice | Multi-step trip planning with GOAP |
| 7 | [compliance-checker](../jaiclaw-examples/compliance-checker/) | Embabel | Embabel, Documents, Audit | Document compliance verification |
| 8 | [incident-responder](../jaiclaw-examples/incident-responder/) | Embabel | Embabel, K8s Tools | DevOps incident triage with health checks and remediation |
| 9 | [data-pipeline](../jaiclaw-examples/data-pipeline/) | Embabel | Embabel, Documents | ETL orchestrator with schema validation and approval |
| 10 | [document-qa](../jaiclaw-examples/document-qa/) | Documents | Documents, Memory, Compaction | PDF ingestion and semantic search Q&A |
| 11 | [telegram-docstore](../jaiclaw-examples/telegram-docstore/) | Documents | DocStore, Telegram | Telegram bot for document management and semantic search |
| 12 | [research-assistant](../jaiclaw-examples/research-assistant/) | Documents | Documents, Browser | Multi-source research with structured report generation |
| 13 | [content-pipeline](../jaiclaw-examples/content-pipeline/) | Media | Media, Documents, Plugin | Multi-modal content analysis |
| 14 | [meeting-assistant](../jaiclaw-examples/meeting-assistant/) | Voice | Voice, Identity, Slack | Meeting transcription and summary |
| 15 | [helpdesk-bot](../jaiclaw-examples/helpdesk-bot/) | Security | Gateway, Security | Multi-tenant support bot |
| 16 | [voice-call-demo](../jaiclaw-examples/voice-call-demo/) | Voice | Voice Call, Twilio | Telephony with outbound reminders and inbound customer service |
| 17 | [security-handshake](../jaiclaw-examples/security-handshake/) | Security | Tools Security | LLM-driven ECDH key exchange and session token bootstrap |
| 18 | [security-handshake-server](../jaiclaw-examples/security-handshake-server/) | Security | Tools Security, MCP | MCP server implementing full ECDH P-256 security handshake |
| 19 | [oauth-provider-demo](../jaiclaw-examples/oauth-provider-demo/) | Security | Identity | OAuth-gated LLM access with PKCE and device code flows |
| 20 | [code-scaffolder](../jaiclaw-examples/code-scaffolder/) | Developer | Code, Plugin | Project scaffolding agent that generates complete project structures |
| 21 | [canvas-dashboard](../jaiclaw-examples/canvas-dashboard/) | Developer | Canvas | On-demand interactive HTML dashboards with Chart.js |
| 22 | [mcp-docs-server](../jaiclaw-examples/mcp-docs-server/) | MCP | Docs, Gateway | MCP server exposing JaiClaw docs as resources with search |
| 23 | [gemma4-local](../jaiclaw-examples/gemma4-local/) | Local LLM | Ollama, Gateway | Gemma 4 chatbot running fully local via Ollama with function calling |
| 24 | [camel-html-summarizer](../jaiclaw-examples/camel-html-summarizer/) | Camel | Camel, Documents | HTML page summarization via Apache Camel routes |
| 25 | [camel-html-summarizer-telegram](../jaiclaw-examples/camel-html-summarizer-telegram/) | Camel | Camel, Telegram | HTML summarizer with Telegram delivery |
| 26 | [camel-html-summarizer-embabel](../jaiclaw-examples/camel-html-summarizer-embabel/) | Camel | Camel, Embabel | HTML summarizer with Embabel GOAP orchestration |
| 27 | [camel-pdf-filler](../jaiclaw-examples/camel-pdf-filler/) | Camel | Camel, Documents | PDF form filling via Apache Camel routes |
| 28 | [camel-pdf-filler-telegram](../jaiclaw-examples/camel-pdf-filler-telegram/) | Camel | Camel, Telegram, Documents | PDF form filling with Telegram integration |
| 29 | [pipeline-e2e](../jaiclaw-examples/pipeline-e2e/) | Pipeline | Pipeline | Pipeline-module e2e fixture — code + inline + per-file YAML sources, HTTP/actuator surface |
| 30 | [support-triage-pipeline](../jaiclaw-examples/support-triage-pipeline/) | Pipeline | Pipeline, Shell | 6-stage Tier-1 ticket triage (MANUAL trigger, confidence-based escalation, Spring Shell driver) |
| 31 | [invoice-processor](../jaiclaw-examples/invoice-processor/) | Pipeline | Pipeline, Shell | 5-stage AP invoice ingestion (FILE trigger + `DEAD_LETTER` strategy, JSONL audit) |
| 32 | [aiops-incident-responder](../jaiclaw-examples/aiops-incident-responder/) | Pipeline | Pipeline, Shell | 6-stage alert triage + auto-remediation + 5-Whys post-mortem (CAMEL_URI trigger, embedded runbook library) |
| 33 | [competitive-intel-briefing](../jaiclaw-examples/competitive-intel-briefing/) | Pipeline | Pipeline, Shell | 5-stage weekday CI digest (CRON via quartz + filesystem signal-cache diff, markdown briefing on disk) |
| 34 | [sales-enrichment-pipeline](../jaiclaw-examples/sales-enrichment-pipeline/) | Pipeline | Pipeline, Shell | 5-stage nightly lead enrichment (CRON + per-tick queue-pop batch loop) |
| 35 | [contract-reviewer](../jaiclaw-examples/contract-reviewer/) | Pipeline | Pipeline, Shell | 6-stage contract review (FILE trigger + `RETRY_THEN_FAIL`, inline playbook, risk-score routing) |
| 36 | [onboarding-intake](../jaiclaw-examples/onboarding-intake/) | Rules | Rules | Drools-driven customer onboarding intake |
| 37 | [procurement-approval](../jaiclaw-examples/procurement-approval/) | Rules | Rules | Drools-driven procurement approval workflow |
| 38 | [tax-advisor](../jaiclaw-examples/tax-advisor/) | Rules | Rules | Drools-driven tax-advice rule evaluation |
| 39 | [support-triage](../jaiclaw-examples/support-triage/) | Embabel | Embabel | Embabel-only support-triage variant (compare with row 30 for pipeline-vs-embabel) |

## Quick Start

```bash
# Build all modules (from project root)
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
./mvnw install -DskipTests

# Run any example
cd jaiclaw-examples/daily-briefing
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

---

## Cron Examples

### 1. Daily Briefing

**Modules:** jaiclaw-cron, jaiclaw-channel-telegram, jaiclaw-channel-email

Scheduled morning briefing that runs at 7 AM on weekdays. The agent fetches weather and news via custom tools, then delivers a formatted digest to Telegram and Email.

**Key classes:**
- `BriefingCronConfig` — registers the cron job with CronService
- `WeatherTool` — ToolCallback that fetches weather data
- `NewsTool` — ToolCallback that fetches news headlines

```
CronService → CronJob("0 7 * * MON-FRI") → AgentRuntime → WeatherTool + NewsTool → Telegram/Email
```

### 2. Sales Report

**Modules:** jaiclaw-cron, jaiclaw-canvas

Weekly sales dashboard generated every Monday at 9 AM. The agent collects sales data via a custom tool and renders an HTML dashboard using the Canvas module.

**Key classes:**
- `SalesReportCronConfig` — weekly cron job registration
- `SalesFetchTool` — ToolCallback that retrieves sales metrics

```
CronService → CronJob("0 9 * * MON") → AgentRuntime → SalesFetchTool → Canvas (HTML dashboard)
```

### 3. Price Monitor

**Modules:** jaiclaw-cron, jaiclaw-browser, jaiclaw-channel-sms

Hourly price checker that monitors product pages. When prices drop below a target, it sends SMS alerts via Twilio.

**Key classes:**
- `PriceCheckCronConfig` — hourly cron job registration
- `PriceCheckTool` — ToolCallback that checks product prices (uses BrowserService in production)

```
CronService → CronJob("0 * * * *") → AgentRuntime → PriceCheckTool → SMS alert (if below target)
```

---

## Embabel Examples

### 4. Code Review Bot

**Modules:** jaiclaw-starter-embabel, jaiclaw-canvas, jaiclaw-plugin-sdk

GOAP-orchestrated code review. The Embabel planner automatically chains two actions: analyze the diff, then generate a structured review.

**Key classes:**
- `CodeReviewAgent` — `@Agent` with `@Action` and `@AchievesGoal`
- `DiffAnalysis` — blackboard domain object (intermediate state)
- `ReviewComplete` — goal condition
- `CodeReviewPlugin` — `JaiClawPlugin` that registers a `GetDiffTool`

```
GOAP Planner: String(diff) → analyzeDiff → DiffAnalysis → generateReview → ReviewComplete
```

### 5. Travel Planner

**Modules:** jaiclaw-starter-embabel, jaiclaw-browser, jaiclaw-voice

Multi-step trip planning. The GOAP planner researches flights and hotels (potentially in parallel), then assembles a complete itinerary with budget analysis.

**Key classes:**
- `TravelPlannerAgent` — `@Agent` with three `@Action` methods
- `TravelRequest` — input domain object
- `FlightOptions`, `HotelOptions` — intermediate blackboard state
- `TripPlan` — goal condition

```
GOAP Planner: TravelRequest → searchFlights → FlightOptions
                             → searchHotels → HotelOptions
              FlightOptions + HotelOptions + TravelRequest → assemblePlan → TripPlan
```

### 6. Compliance Checker

**Modules:** jaiclaw-starter-embabel, jaiclaw-documents, jaiclaw-audit

GOAP-based document compliance verification. Extracts policy rules from a compliance document, then checks target documents against those rules with full audit trail.

**Key classes:**
- `ComplianceAgent` — `@Agent` with extractPolicy and checkCompliance actions
- `PolicyDocument` — extracted policy rules (intermediate state)
- `ComplianceReport` — goal condition with pass/fail, findings, and score

```
GOAP Planner: String(policy) → extractPolicy → PolicyDocument → checkCompliance → ComplianceReport
```

---

## Other Examples

### 7. Document Q&A

**Modules:** jaiclaw-documents, jaiclaw-memory, jaiclaw-compaction

PDF ingestion and semantic search Q&A. Documents are parsed, chunked, and indexed. Questions are answered by searching for relevant passages and synthesizing answers with citations.

**Key classes:**
- `DocumentIngestTool` — ingests documents into the knowledge base
- `DocumentSearchTool` — semantic search over ingested documents

```
User → ingest_document → parse + chunk + index
User → "question?" → search_documents → relevant passages → LLM → answer with citations
```

### 8. Meeting Assistant

**Modules:** jaiclaw-voice, jaiclaw-identity, jaiclaw-channel-slack

Meeting transcription and summarization. Processes audio recordings via STT, identifies speakers with cross-channel identity linking, and delivers summaries to Slack.

**Key classes:**
- `TranscriptionTool` — transcribes meeting audio (uses VoiceService STT in production)
- `SummaryTool` — stores meeting summaries and action items

```
Audio file → TranscriptionTool (STT) → transcript → LLM → summary + action items → Slack
```

### 9. Helpdesk Bot

**Modules:** jaiclaw-gateway, jaiclaw-security

Multi-tenant support bot with FAQ search and ticket creation. Demonstrates API key authentication and per-tenant session isolation.

**Key classes:**
- `FaqTool` — searches FAQ knowledge base (tenant-aware via ToolContext)
- `TicketTool` — creates support tickets for unresolved issues

```
User → X-API-Key auth → tenant resolution → FaqTool → answer or TicketTool → ticket created
```

### 10. Content Pipeline

**Modules:** jaiclaw-media, jaiclaw-documents, jaiclaw-plugin-sdk

Multi-modal content analysis pipeline. A plugin registers tools for image analysis and metadata extraction, processing images, audio, and documents into structured metadata.

**Key classes:**
- `ContentAnalysisPlugin` — `JaiClawPlugin` registering AnalyzeImageTool and ExtractMetadataTool

```
Image/PDF/Audio → ContentAnalysisPlugin → analyze_image / extract_metadata → structured metadata
```

---

## Pipeline Examples

Pipeline examples exercise the `jaiclaw-pipeline` module's DSL, triggers, and Phase A–F UX surfaces. Each app drops into an interactive Spring Shell and is opt-in via `jaiclaw.pipeline.enabled: true` in `application.yml`.

### 29. Pipeline E2E

**Modules:** jaiclaw-starter-pipeline, jaiclaw-audit, jaiclaw-plugin-sdk, spring-boot-starter-web, spring-boot-starter-actuator

End-to-end fixture used by `e2e/run-e2e-tests.sh` scenario 6. Wires all three definition sources at once:

- `processor-pipe` from Java code (`E2ePipelines.java` extending `JaiClawPipeline`)
- `processor-pipe-from-file` from `src/main/resources/jaiclaw/pipelines/processor-pipe-from-file.yml`
- `broken-pipe` from `application-broken.yml` (intentionally fails validation — demonstrates Phase A's consolidated error message)

Drives `POST /api/pipelines/{id}/trigger`, `/actuator/pipelines`, and `{{input}}` template resolution.

### 30. Support Triage Pipeline

**Modules:** jaiclaw-starter-pipeline, jaiclaw-starter-shell, spring-ai-starter-model-anthropic

6 stages: `classify-and-sentiment` (AGENT) → `context-fetch` (PROCESSOR stub CRM) → `knowledge-retrieval` (AGENT) → `resolve-or-draft` (AGENT, emits `confidence:`) → `escalation-gate` (PROCESSOR, parses confidence + VIP) → `close-and-log` (PROCESSOR). MANUAL trigger, driven by the shell command `trigger <ticket text>`. Demonstrates `.then()` DSL, `{{pipeline.executionId}}` in output template, confidence-based escalation.

### 31. Invoice Processor

**Modules:** jaiclaw-starter-pipeline, jaiclaw-starter-shell, spring-ai-starter-model-anthropic

5 stages: classify (AGENT) → extract (AGENT) → validate (PROCESSOR PO-database stub) → approve-or-flag (AGENT) → notify (PROCESSOR, appends JSONL). **FILE trigger** watching `~/.jaiclaw/invoice-processor/inbox`; `errorStrategy: DEAD_LETTER` routes parse failures to a log queue. Shell `inbox` writes a synthetic invoice.

### 32. AIOps Incident Responder

**Modules:** jaiclaw-starter-pipeline, jaiclaw-starter-shell, spring-ai-starter-model-anthropic

6 stages: triage → root-cause → runbook-lookup (3× AGENT, embedded 5-runbook library) → auto-remediate (PROCESSOR, parses verbs) → escalate-or-resolve (PROCESSOR) → post-mortem-draft (AGENT, 5-Whys). **CAMEL_URI trigger** at `direct:incident-alert`; the Phase E convergence route makes `PipelineGateway.trigger("aiops-incident", ...)` work too. Shell command `incident <text>` plus `replay <executionId>`.

### 33. Competitive Intel Briefing

**Modules:** jaiclaw-starter-pipeline, jaiclaw-starter-shell, spring-ai-starter-model-anthropic, camel-quartz-starter

5 stages: collect-signals (AGENT, web-search tool) → detect-changes (PROCESSOR, filesystem-cache diff) → synthesize (AGENT) → impact-analysis (AGENT) → format-briefing (PROCESSOR, writes `<date>.md` to disk). **CRON trigger** `0 0 7 ? * MON-FRI` (real cron expression honored via Camel quartz). `@ConfigurationProperties` for the competitor list. Shell `run-now` short-circuits the schedule.

### 34. Sales Enrichment Pipeline

**Modules:** jaiclaw-starter-pipeline, jaiclaw-starter-shell, spring-ai-starter-model-anthropic, camel-quartz-starter

5 stages: load-new-leads (PROCESSOR, pops one lead off in-memory queue) → enrich (AGENT, web-search) → score (AGENT) → draft-outreach (AGENT) → write-back (PROCESSOR, appends JSONL). **CRON trigger** `0 0 2 * * ?` with per-tick batch-loop pattern: every cron firing handles exactly one lead, keeping each executionId traceable. Shell `add-lead`, `run-now`, `list-enriched`.

### 35. Contract Reviewer

**Modules:** jaiclaw-starter-pipeline, jaiclaw-starter-shell, spring-ai-starter-model-anthropic

6 stages: extract-structure → playbook-check (5-rule playbook embedded in prompt) → risk-score → redline → compliance-scan (5× AGENT) → route (PROCESSOR, AUTO_APPROVE / COUNSEL_REVIEW / REJECT). **FILE trigger** with `errorStrategy: RETRY_THEN_FAIL` + `maxRetries: 2`. Shell `inbox` writes a synthetic MSA.

---

## Local LLM Examples

### 12. Gemma 4 Local

**Modules:** jaiclaw-gateway, spring-ai-starter-model-ollama

Conversational chatbot running Google Gemma 4 locally via Ollama. Demonstrates native function calling with a fully open-source model (Apache 2.0) — zero cloud API dependencies.

**Key classes:**
- `Gemma4ToolConfig` — registers CurrentTimeTool and CalculateTool via ToolRegistry
- `CurrentTimeTool` — timezone-aware clock (demonstrates tool parameter handling)
- `CalculateTool` — arithmetic expression evaluator

```
User → AgentRuntime → Ollama (localhost:11434) → Gemma 4 12B → function call → CurrentTimeTool/CalculateTool → response
```

Includes `docker-compose.yml` for starting Ollama with Gemma 4 auto-pulled. Supports all model sizes (2B, 4B, 12B, 27B) via the `OLLAMA_MODEL` env var.

---

## Building All Examples

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle

# Compile all examples
./mvnw compile -pl jaiclaw-examples -am

# Package all examples
./mvnw package -pl jaiclaw-examples -am -DskipTests
```

## Project Structure

Each example follows the same layout:

```
example-name/
  pom.xml                          Maven POM with example-specific dependencies
  README.md                        How to build, configure, and run
  src/main/java/io/jaiclaw/examples/
    ExampleApplication.java        @SpringBootApplication entry point
    *Tool.java                     Custom ToolCallback implementations
    *Agent.java                    Embabel @Agent classes (Embabel examples only)
    *Plugin.java                   JaiClawPlugin implementations (plugin examples only)
    *CronConfig.java               Cron job registration (cron examples only)
  src/main/resources/
    application.yml                Spring Boot configuration
    skills/*.md                    Custom skill definitions
```
