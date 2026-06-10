# JaiClaw Documentation

> **First time here?** Start at
> [user/GETTING-STARTED.md](user/GETTING-STARTED.md). It walks the
> three launch paths (Docker, library, CLI) and the top five
> first-time failure modes.

This index is grouped by audience. The `user/` tree is the canonical
reference for anyone running, configuring, or building on JaiClaw. The
`dev/` tree is for contributors and framework maintainers.

---

## For users / adopters

### Onboarding

- [**GETTING-STARTED.md**](user/GETTING-STARTED.md) — your first 10
  minutes; three launch paths compared
- [**CONFIGURATION.md**](user/CONFIGURATION.md) — minimal-viable
  config + common recipes (Anthropic+Telegram, OpenAI+Slack,
  Ollama-only)
- [**SKILLS.md**](user/SKILLS.md) — what skills are, how to author,
  bundled-skill cost tuning (~26K tokens/request default)
- [WHAT-IS-JAICLAW.md](user/WHAT-IS-JAICLAW.md) — beginner-friendly
  primer
- [WHAT-IS-AGENTIC-AI.md](user/WHAT-IS-AGENTIC-AI.md) —
  plain-language explainer

### Operating

- [**OPERATIONS.md**](user/OPERATIONS.md) — running, deploying,
  profiles, security modes, environment variables (deep reference)
- [**PRODUCTION-DEPLOYMENT.md**](user/PRODUCTION-DEPLOYMENT.md) — K8s
  manifests, Helm values, secrets, Actuator/Micrometer observability,
  health probes, security hardening, cloud-provider notes, runbook
- [**EXAMPLES.md**](user/EXAMPLES.md) — full catalog of 40 example
  applications
- [CLI-REFERENCE.md](user/CLI-REFERENCE.md) — `bin/jaiclaw` commands
- [TELEGRAM-SETUP.md](user/TELEGRAM-SETUP.md) — channel walkthrough
- [features/](user/features/) — per-feature reference (browser,
  canvas, compaction, cron, group-chat routing, identity, voice,
  voice-call, workspace memory)

### Extending JaiClaw

- [**AUTHORING-TOOLS.md**](user/AUTHORING-TOOLS.md) — write a custom
  tool the LLM can call
- [**AUTHORING-SKILLS.md**](user/AUTHORING-SKILLS.md) — teach the
  agent domain knowledge via Markdown skills
- [**AUTHORING-CHANNELS.md**](user/AUTHORING-CHANNELS.md) — build a
  new channel adapter for a messaging platform we don't ship

### Cost & tuning

- [TOKEN-OPTIMIZATION.md](user/TOKEN-OPTIMIZATION.md) — reduce token
  overhead in prompts
- [SKILLS.md](user/SKILLS.md) § Cost — bundled-skills budget
- [OLLAMA-TUNING-GUIDE.md](user/OLLAMA-TUNING-GUIDE.md) — Ollama
  parameter tuning
- [GEMMA4-HARDWARE-GUIDE.md](user/GEMMA4-HARDWARE-GUIDE.md) — local
  Gemma 4 hardware requirements & VRAM budgets

### Reference

- [VERSIONS.md](user/VERSIONS.md) — version history
- [anthropic-models-spring-ai.md](user/anthropic-models-spring-ai.md)
  — Anthropic model ID reference for Spring AI
- [JAICLAW-FROM-PERSONAL-TO-ENTERPRISE.md](user/JAICLAW-FROM-PERSONAL-TO-ENTERPRISE.md)
  — scaling from a personal CLI to multi-tenant enterprise

---

## For contributors / framework maintainers

- [ARCHITECTURE.md](dev/ARCHITECTURE.md) — module DAG, message flow,
  K8s deployment patterns
- [AGENTIC-WORKFLOW.md](dev/AGENTIC-WORKFLOW.md) — tool loops, hooks,
  context compaction internals
- [PIPELINE-STRATEGY.md](dev/PIPELINE-STRATEGY.md) — `jaiclaw-pipeline`
  design + DSL
- [PIPELINE_UX_IMPROVEMENTS.md](dev/PIPELINE_UX_IMPROVEMENTS.md) —
  closing notes on the Phase A–F UX overhaul
- [CAMEL-INTEGRATION-DESIGN.md](dev/CAMEL-INTEGRATION-DESIGN.md) —
  Apache Camel adapter design
- [OAUTH-IMPLEMENTATION-PLAN.md](dev/OAUTH-IMPLEMENTATION-PLAN.md) —
  OAuth credential management roadmap
- [OAUTH-INTEGRATION-TESTS.md](dev/OAUTH-INTEGRATION-TESTS.md) —
  integration-test architecture
- [multi-tenancy-architecture.md](dev/multi-tenancy-architecture.md)
  — tenant isolation design
- [FEATURE-COMPARISON.md](dev/FEATURE-COMPARISON.md) — OpenClaw vs
  JaiClaw vs Embabel matrix
- [OPENCLAW-VS-JAICLAW-V2.md](dev/OPENCLAW-VS-JAICLAW-V2.md) —
  detailed parity matrix

Maintainer plumbing — [CONTRIBUTING.md](../CONTRIBUTING.md),
[SECURITY.md](../SECURITY.md), [CHANGELOG.md](../CHANGELOG.md),
[CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md), and the audit doc below
all live at the repo root or here.

---

## FAQs

- [How to create skills](faqs/how-to-create-skills.md)
- [How to load skills](faqs/how-to-load-skills.md)

---

## Strategy & roadmap

- [**POSITIONING.md**](POSITIONING.md) — what JaiClaw is, what it isn't,
  and when to choose it over Spring AI / LangChain4j / Embabel-alone
- [**ROAD-TO-1.0.md**](ROAD-TO-1.0.md) — what's `@Stable` today, what's
  still `@Experimental`, gates between 0.8 → 0.9 → 1.0
- [**MIGRATION-0.8.md**](MIGRATION-0.8.md) — 0.7.x → 0.8.0 upgrade guide
  (hard-break release)

## Audit & remediation

- [CODEBASE-ANALYSIS-2026-06-10.md](CODEBASE-ANALYSIS-2026-06-10.md)
  — independent codebase audit and the remediation plan being worked
  through across PR1–PR8. Stays at the docs/ root until remediation
  closes.

---

## Internal / non-public docs

Planning, sales positioning, parity-tracking, ideation, and other
non-adopter-facing material lives outside this repo at
`/Users/tap/dev/docs/jaiclaw/internal/`. The split happened in the
2026-06-10 docs reorg per [CODEBASE-ANALYSIS-2026-06-10.md](CODEBASE-ANALYSIS-2026-06-10.md)
§ 1.4.
