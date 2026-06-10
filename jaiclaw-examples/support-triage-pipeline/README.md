# Support Triage Pipeline

Tier-0/1 support automation built on the JaiClaw pipeline module.

## Problem

Support teams spend the bulk of their time on a thin slice of repetitive tasks:
classifying incoming tickets, looking up the customer record, drafting boilerplate
replies, and escalating the long tail that the script can't handle. Klarna's
public deployment replaced ~700 FTEs with an AI-driven first-line; average ROI
on this style of automation is $3–8 per $1 invested. This example is the smallest
possible end-to-end demo of that pattern.

## Solution

A six-stage pipeline that mixes AGENT (LLM) and PROCESSOR (Spring bean) stages:

1. **classify-and-sentiment** — AGENT — extract intent, urgency, sentiment
2. **context-fetch** — PROCESSOR — stub CRM lookup keyed by the currently-selected customer id
3. **knowledge-retrieval** — AGENT — pick the relevant canned KB snippet
4. **resolve-or-draft** — AGENT — draft a customer-facing reply plus a confidence score
5. **escalation-gate** — PROCESSOR — escalate if VIP or confidence < 0.7
6. **close-and-log** — PROCESSOR — append a case-notes line for the audit trail

The whole thing is driven from an interactive Spring Shell — no Slack channels,
no webhooks, no API tokens. Switch in a real CRM / KB / Slack channel by swapping
the corresponding `Function<String,String>` Spring bean.

## Architecture

```
Spring Shell      ─trigger "<ticket>"─▶  PipelineGateway
                                              │
                                              ▼
        ┌──────────────────────────────────────────────────────────┐
        │ Pipeline: support-triage                                │
        │  classify-and-sentiment  ─▶  context-fetch  (CRM stub)  │
        │       (AGENT)                  (PROCESSOR)              │
        │  knowledge-retrieval     ─▶  resolve-or-draft           │
        │       (AGENT)                  (AGENT)                  │
        │  escalation-gate         ─▶  close-and-log              │
        │       (PROCESSOR)              (PROCESSOR)              │
        └──────────────────────────────────────────────────────────┘
                                              │
                                              ▼
                                  PipelineExecutionTracker
                                              │
   Spring Shell    ◀─history / last-result────┘
```

## Design

- **Stub all external systems.** CRM is an in-memory `Map<String,String>` with
  three seeded accounts. KB is a canned answer baked into the agent's prompt.
  Everything resolves locally so the example runs without secrets beyond the
  LLM key.
- **Shell as the output surface.** The pipeline's `output()` uses `LOG`; the
  shell reads the tracker's `recentExecutions` to render `executions` and
  `last-result`. Operators get a discoverable demo without standing up Slack.
- **Confidence-based escalation.** The `resolve-or-draft` agent is instructed
  to end its reply with `confidence: <0..1>`. The `escalation-gate` PROCESSOR
  parses that and routes either to `AUTO_RESOLVED:` or `ESCALATED:`.
- **{{pipeline.executionId}} surfaced** in the output template so log lines are
  correlatable with the actuator `byId` endpoint.

## Prerequisites

- Java 21
- `ANTHROPIC_API_KEY` exported (or another Spring AI provider configured via
  `application.yml`)

## Build & Run

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle

# Build (installs the pipeline module transitively).
./mvnw package -pl :jaiclaw-example-support-triage-pipeline -am -DskipTests -o

# Run — drops you into an interactive Spring Shell.
ANTHROPIC_API_KEY=sk-ant-... \
  java -jar jaiclaw-examples/support-triage-pipeline/target/jaiclaw-example-support-triage-pipeline-*.jar
```

Inside the shell:

```
shell:> set-customer CUST-002          # picks the VIP account
shell:> trigger My order #1234 never arrived and I want a refund
Submitted executionId=… (customer=CUST-002). Run `executions` or `last-result` to see the outcome.

shell:> executions
executionId                           status     ms      current/last stage
…                                     SUCCESS    1842    -

shell:> last-result
executionId=…
status=SUCCESS
totalMs=1842
--- per-stage durations (ms) ---
  classify-and-sentiment = 612
  context-fetch          = 1
  knowledge-retrieval    = 438
  resolve-or-draft       = 691
  escalation-gate        = 0
  close-and-log          = 0
```

The full resolved output (with the customer-facing draft) lands in the app log
under the `io.jaiclaw.pipeline.output.support-triage` logger.

## Extension points

- **Real CRM:** replace `SupportTriageBeans#crmLookup` with a bean that calls
  Salesforce / HubSpot / your own.
- **Real KB / RAG:** swap `knowledge-retrieval` for an AGENT whose `systemPrompt`
  uses your KB-search tool, or split it into a PROCESSOR that does a vector
  store hit + an AGENT that summarises.
- **Real escalation:** add a Slack channel adapter and set
  `output().channel("slack")`, or change `close-and-log` to post to your
  ticketing system.
- **Authentication:** flip `jaiclaw.security.mode` from `none` to `api-key`
  and configure a key in `~/.jaiclaw/api-keys.json`.
