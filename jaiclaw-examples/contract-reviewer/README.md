# Contract Reviewer

Six-stage contract review pipeline built on the JaiClaw pipeline module.
Showcases the **FILE trigger**, an **embedded playbook**, and
`errorStrategy: RETRY_THEN_FAIL` for transient LLM failures.

## Problem

Outside counsel charges $300–$600/hour for contract review. Salesforce
eliminated $5M+ in legal costs with AI contract automation; CLM is a
$3.5B+ market growing 13% annually. The first thing legal-ops teams build
is a "playbook" review pipeline that catches the same handful of clauses
over and over.

## Solution

A six-stage pipeline triggered by a contract dropped into
`~/.jaiclaw/contract-reviewer/inbox`. The agent stages are sequenced so
each downstream stage gets all the upstream signal it needs:

1. **extract-structure** (AGENT) — parties, dates, term, payment, liability
2. **playbook-check** (AGENT, playbook inline) — OK / REVIEW / REJECT per rule
3. **risk-score** (AGENT) — single 1-10 risk score + rationale
4. **redline** (AGENT) — suggested language for REVIEW/REJECT items
5. **compliance-scan** (AGENT) — GDPR DPA, IP assignment, jurisdiction
6. **route** (PROCESSOR) — AUTO_APPROVE / COUNSEL_REVIEW / REJECT based on risk

A bad LLM call (rate-limit, 502) doesn't kill the pipeline — `RETRY_THEN_FAIL`
gives it two more shots before bubbling up.

## Architecture

```
~/.jaiclaw/contract-reviewer/inbox/*.txt              (FILE trigger)
                  │
                  ▼
   ┌────────────────────────────────────────────────────────────────┐
   │ Pipeline: contract-reviewer   errorStrategy: RETRY_THEN_FAIL   │
   │   extract-structure  ─▶  playbook-check  ─▶  risk-score        │
   │   (AGENT)                 (AGENT, playbook inline)   (AGENT)   │
   │   redline  ─▶  compliance-scan  ─▶  route                      │
   │   (AGENT)       (AGENT)            (PROCESSOR)                 │
   └────────────────────────────────────────────────────────────────┘
                  │
                  ▼
            log + ROUTE marker (AUTO_APPROVE | COUNSEL_REVIEW | REJECT)
```

## Design

- **Playbook inline.** Five short rules embedded in the `playbook-check`
  agent's system prompt. No external storage needed; the contract bar takes
  the playbook home with the example. Swap in a vector-store-backed
  PROCESSOR or AGENT for a long playbook.
- **RETRY_THEN_FAIL strategy.** LLM provider blips are common and transient;
  the pipeline gets up to two redeliveries before surfacing a failure.
  This is the Phase A retry plumbing.
- **Failure-closed risk score.** If the AGENT's output doesn't contain
  `risk_score: N`, the router assumes 10 (max risk) so the contract lands
  in REJECT — better than silently auto-approving a poorly-parsed document.
- **Synthetic contracts via the shell.** `inbox` with no args writes a
  realistic-but-risky sample contract into the watched directory so the
  demo doesn't require importing real Word docs.

## Prerequisites

- Java 21
- `ANTHROPIC_API_KEY`

## Build & Run

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw package -pl :jaiclaw-example-contract-reviewer -am -DskipTests -o
ANTHROPIC_API_KEY=sk-ant-... \
  java -jar jaiclaw-examples/contract-reviewer/target/jaiclaw-example-contract-reviewer-*.jar
```

Inside the shell:

```
shell:> inbox                          # drops the default risky-MSA sample
Wrote ~/.jaiclaw/contract-reviewer/inbox/contract-….txt …

shell:> executions
…                                     SUCCESS    12400

shell:> last-result
executionId=…
status=SUCCESS
totalMs=12400
--- per-stage durations (ms) ---
  extract-structure  = 1410
  playbook-check     = 2210
  risk-score         = 980
  redline            = 4520
  compliance-scan    = 3260
  route              = 0
```

The full routing decision (with the redline drafts) lands in the application
log under `io.jaiclaw.pipeline.output.contract-reviewer`.

## Extension points

- **Word / PDF parsing:** wrap the FILE trigger body in a Tika / docx4j
  parser before `extract-structure`.
- **Real CLM integration:** add a final PROCESSOR or `CAMEL_URI` output that
  pushes the routed decision to iManage / Ironclad / Docusign CLM.
- **Real playbook RAG:** swap `playbook-check` for an AGENT whose
  `systemPrompt` consults your vector store.
- **Reviewer hand-off:** when `ROUTE: COUNSEL_REVIEW` fires, kick off another
  pipeline that pages an attorney via the same `PipelineGateway` API.
