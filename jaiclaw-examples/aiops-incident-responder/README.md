# AIOps Incident Responder

Automated SRE on-call assistant built on the JaiClaw pipeline module.
Showcases the **CAMEL_URI trigger** plus a fully embedded runbook library.

## Problem

Production downtime costs enterprises $100K–$5M per hour, but most of the
time-to-resolution is spent triaging the alert and looking up the right
runbook. AIOps-style automation reduces MTTR by 50–70% on repetitive
incidents (5xx spikes, OOMs, cache evictions, queue backlogs).

## Solution

A six-stage pipeline whose primary trigger is a Camel direct URI
(`direct:incident-alert`) so any internal route — a webhook receiver, a
Kafka consumer, an alertmanager bridge — can fire it. The same pipeline is
also reachable via the Spring Shell `incident` command and via the
standard `PipelineGateway` HTTP endpoint (without extra wiring — Phase E's
convergence route registers `direct:pipeline-aiops-incident` for every
pipeline regardless of its primary trigger).

Stages:

1. **triage** (AGENT) — severity / system / blast radius
2. **root-cause** (AGENT) — probable cause + recent changes
3. **runbook-lookup** (AGENT) — pick a library entry; emit action verbs
4. **auto-remediate** (PROCESSOR) — parses the verb and "executes" a safe action
5. **escalate-or-resolve** (PROCESSOR) — closes the incident or pages on-call
6. **post-mortem-draft** (AGENT) — 5-Whys + conclusion

## Architecture

```
direct:incident-alert      OR    Spring Shell `incident`     OR    PipelineGateway HTTP
        │                                │                                  │
        └────────────────────────────────┼──────────────────────────────────┘
                                         ▼
            ┌──────────────────────────────────────────────────┐
            │  triage → root-cause → runbook-lookup            │
            │  (AGENT)   (AGENT)    (AGENT, runbooks inline)   │
            │  → auto-remediate → escalate-or-resolve          │
            │     (PROCESSOR)        (PROCESSOR)               │
            │  → post-mortem-draft (AGENT)                     │
            └──────────────────────────────────────────────────┘
```

## Design

- **CAMEL_URI trigger.** Showcases the fact that pipelines can be glued into
  arbitrary Camel routes. The convergence route added in Phase E means the
  Spring Shell still drives the pipeline via the gateway without needing a
  custom controller.
- **Runbook library inlined as a system-prompt constant.** Five entries,
  enough for a believable demo, zero external storage required.
- **PROCESSOR for remediation.** The `auto-remediate` stage is deliberately a
  Spring bean, not an LLM call, because real auto-remediation has to be
  deterministic and auditable. The bean's keyword matching is intentionally
  trivial — swap it for real Kubernetes / cloud-API calls in production.
- **`{{pipeline.executionId}}`** appears in the output template so log lines
  are correlatable with the actuator `byId` endpoint.

## Prerequisites

- Java 21
- `ANTHROPIC_API_KEY`

## Build & Run

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw package -pl :jaiclaw-example-aiops-incident-responder -am -DskipTests -o
ANTHROPIC_API_KEY=sk-ant-... \
  java -jar jaiclaw-examples/aiops-incident-responder/target/jaiclaw-example-aiops-incident-responder-*.jar
```

Inside the shell:

```
shell:> incident         # default sample P2 alert
Submitted executionId=… Run `executions` or `last-result`.

shell:> last-result
executionId=…
status=SUCCESS
totalMs=2210
--- per-stage durations (ms) ---
  triage             = 421
  root-cause         = 491
  runbook-lookup     = 388
  auto-remediate     = 0
  escalate-or-resolve = 0
  post-mortem-draft  = 873
```

## Extension points

- **Real alert ingress:** point the trigger at the Camel `kafka:` or
  `netty-http:` URI of your alert pipe. The whole pipeline keeps working —
  only the inbound bridge changes.
- **Real remediation:** replace `AiopsIncidentBeans#remediationBean` with a
  bean that calls `kubectl rollout restart` / your cloud provider's API /
  your platform's safe-action library.
- **Real escalation:** replace `escalationBean` with a PagerDuty / OpsGenie
  client. The PROCESSOR shape stays the same.
- **Runbook RAG:** replace the inlined library with an AGENT call against
  your vector store; the rest of the pipeline doesn't change.
