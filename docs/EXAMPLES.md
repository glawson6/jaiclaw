# JClaw Examples

10 example applications demonstrating JClaw framework capabilities. Each is a standalone Spring Boot app that can be built and run independently.

## Overview

| # | Example | Category | Modules | Description |
|---|---------|----------|---------|-------------|
| 1 | [daily-briefing](../jclaw-examples/daily-briefing/) | Cron | Cron, Telegram, Email | Scheduled morning briefing with news and weather |
| 2 | [sales-report](../jclaw-examples/sales-report/) | Cron | Cron, Canvas | Weekly sales dashboard with HTML report |
| 3 | [price-monitor](../jclaw-examples/price-monitor/) | Cron | Cron, Browser, SMS | Hourly price checker with SMS alerts |
| 4 | [code-review-bot](../jclaw-examples/code-review-bot/) | Embabel | Embabel, Canvas, Plugin | GOAP-orchestrated PR code review |
| 5 | [travel-planner](../jclaw-examples/travel-planner/) | Embabel | Embabel, Browser, Voice | Multi-step trip planning with GOAP |
| 6 | [compliance-checker](../jclaw-examples/compliance-checker/) | Embabel | Embabel, Documents, Audit | Document compliance verification |
| 7 | [document-qa](../jclaw-examples/document-qa/) | Documents | Documents, Memory, Compaction | PDF ingestion and semantic search Q&A |
| 8 | [meeting-assistant](../jclaw-examples/meeting-assistant/) | Voice | Voice, Identity, Slack | Meeting transcription and summary |
| 9 | [helpdesk-bot](../jclaw-examples/helpdesk-bot/) | Security | Gateway, Security | Multi-tenant support bot |
| 10 | [content-pipeline](../jclaw-examples/content-pipeline/) | Media | Media, Documents, Plugin | Multi-modal content analysis |

## Quick Start

```bash
# Build all modules (from project root)
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
./mvnw install -DskipTests

# Run any example
cd jclaw-examples/daily-briefing
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

---

## Cron Examples

### 1. Daily Briefing

**Modules:** jclaw-cron, jclaw-channel-telegram, jclaw-channel-email

Scheduled morning briefing that runs at 7 AM on weekdays. The agent fetches weather and news via custom tools, then delivers a formatted digest to Telegram and Email.

**Key classes:**
- `BriefingCronConfig` — registers the cron job with CronService
- `WeatherTool` — ToolCallback that fetches weather data
- `NewsTool` — ToolCallback that fetches news headlines

```
CronService → CronJob("0 7 * * MON-FRI") → AgentRuntime → WeatherTool + NewsTool → Telegram/Email
```

### 2. Sales Report

**Modules:** jclaw-cron, jclaw-canvas

Weekly sales dashboard generated every Monday at 9 AM. The agent collects sales data via a custom tool and renders an HTML dashboard using the Canvas module.

**Key classes:**
- `SalesReportCronConfig` — weekly cron job registration
- `SalesFetchTool` — ToolCallback that retrieves sales metrics

```
CronService → CronJob("0 9 * * MON") → AgentRuntime → SalesFetchTool → Canvas (HTML dashboard)
```

### 3. Price Monitor

**Modules:** jclaw-cron, jclaw-browser, jclaw-channel-sms

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

**Modules:** jclaw-starter-embabel, jclaw-canvas, jclaw-plugin-sdk

GOAP-orchestrated code review. The Embabel planner automatically chains two actions: analyze the diff, then generate a structured review.

**Key classes:**
- `CodeReviewAgent` — `@Agent` with `@Action` and `@AchievesGoal`
- `DiffAnalysis` — blackboard domain object (intermediate state)
- `ReviewComplete` — goal condition
- `CodeReviewPlugin` — `JClawPlugin` that registers a `GetDiffTool`

```
GOAP Planner: String(diff) → analyzeDiff → DiffAnalysis → generateReview → ReviewComplete
```

### 5. Travel Planner

**Modules:** jclaw-starter-embabel, jclaw-browser, jclaw-voice

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

**Modules:** jclaw-starter-embabel, jclaw-documents, jclaw-audit

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

**Modules:** jclaw-documents, jclaw-memory, jclaw-compaction

PDF ingestion and semantic search Q&A. Documents are parsed, chunked, and indexed. Questions are answered by searching for relevant passages and synthesizing answers with citations.

**Key classes:**
- `DocumentIngestTool` — ingests documents into the knowledge base
- `DocumentSearchTool` — semantic search over ingested documents

```
User → ingest_document → parse + chunk + index
User → "question?" → search_documents → relevant passages → LLM → answer with citations
```

### 8. Meeting Assistant

**Modules:** jclaw-voice, jclaw-identity, jclaw-channel-slack

Meeting transcription and summarization. Processes audio recordings via STT, identifies speakers with cross-channel identity linking, and delivers summaries to Slack.

**Key classes:**
- `TranscriptionTool` — transcribes meeting audio (uses VoiceService STT in production)
- `SummaryTool` — stores meeting summaries and action items

```
Audio file → TranscriptionTool (STT) → transcript → LLM → summary + action items → Slack
```

### 9. Helpdesk Bot

**Modules:** jclaw-gateway, jclaw-security

Multi-tenant support bot with FAQ search and ticket creation. Demonstrates API key authentication and per-tenant session isolation.

**Key classes:**
- `FaqTool` — searches FAQ knowledge base (tenant-aware via ToolContext)
- `TicketTool` — creates support tickets for unresolved issues

```
User → X-API-Key auth → tenant resolution → FaqTool → answer or TicketTool → ticket created
```

### 10. Content Pipeline

**Modules:** jclaw-media, jclaw-documents, jclaw-plugin-sdk

Multi-modal content analysis pipeline. A plugin registers tools for image analysis and metadata extraction, processing images, audio, and documents into structured metadata.

**Key classes:**
- `ContentAnalysisPlugin` — `JClawPlugin` registering AnalyzeImageTool and ExtractMetadataTool

```
Image/PDF/Audio → ContentAnalysisPlugin → analyze_image / extract_metadata → structured metadata
```

---

## Building All Examples

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle

# Compile all examples
./mvnw compile -pl jclaw-examples -am

# Package all examples
./mvnw package -pl jclaw-examples -am -DskipTests
```

## Project Structure

Each example follows the same layout:

```
example-name/
  pom.xml                          Maven POM with example-specific dependencies
  README.md                        How to build, configure, and run
  src/main/java/io/jclaw/examples/
    ExampleApplication.java        @SpringBootApplication entry point
    *Tool.java                     Custom ToolCallback implementations
    *Agent.java                    Embabel @Agent classes (Embabel examples only)
    *Plugin.java                   JClawPlugin implementations (plugin examples only)
    *CronConfig.java               Cron job registration (cron examples only)
  src/main/resources/
    application.yml                Spring Boot configuration
    skills/*.md                    Custom skill definitions
```
