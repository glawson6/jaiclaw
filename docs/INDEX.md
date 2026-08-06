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
- [WEB-ERROR-HANDLING.md](user/WEB-ERROR-HANDLING.md) — framework
  default `@RestControllerAdvice` / `ErrorWebExceptionHandler` (opt
  out with `jaiclaw.web.errors.enabled=false`)
- [SESSION-BACKENDS.md](user/SESSION-BACKENDS.md) — chat-history
  storage: in-memory default vs `jaiclaw-session-redis` for durable
  history across pod restarts
- [**pipeline/**](user/pipeline/) — the four-phase Pipeline Studio guides:
  - [PIPELINE-DASHBOARD.md](user/pipeline/PIPELINE-DASHBOARD.md) — read-only
    dashboard at `/pipelines/dashboard` (Phase 0)
  - [PIPELINE-STUDIO-API.md](user/pipeline/PIPELINE-STUDIO-API.md) — authoring
    REST surface at `/api/pipeline-studio/*` (Phase 1; Phase 3 adds
    deploy + test-run + role authz)
  - [PIPELINE-STUDIO.md](user/pipeline/PIPELINE-STUDIO.md) — React Flow
    SPA served at `/studio` (Phase 2; Phase 3 adds the DeployToolbar)
  - [PIPELINE-PROCESSOR-CATALOG.md](user/pipeline/PIPELINE-PROCESSOR-CATALOG.md) —
    baseline processor pack (Phase 4: ~15 core + 7 AI presets + 6
    Camel templates + Tool/Memory/Document integrations)
- [features/](user/features/) — per-feature reference (browser,
  canvas, compaction, cron, group-chat routing, identity, voice,
  voice-call, workspace memory)

### Compliance

- [**compliance/README.md**](compliance/README.md) — landing page for
  government researchers, ATO assessors, and adopters evaluating
  JaiClaw against U.S. federal + EU regulatory frameworks. Rating
  table + per-regulation deep-dives.
- Per-regulation deep-dives (all under `compliance/`):
  - [section-508.md](compliance/section-508.md) — accessibility (WCAG 2.0 AA)
  - [fedramp.md](compliance/fedramp.md) — cloud service authorization
  - [fisma.md](compliance/fisma.md) — federal information security
  - [nist-800-53.md](compliance/nist-800-53.md) — NIST security & privacy controls (family-by-family table)
  - [fips-140-3.md](compliance/fips-140-3.md) — cryptographic module posture + BC-FIPS integration
  - [cmmc.md](compliance/cmmc.md) — CMMC 2.0 / CUI handling (DoD contractors)
  - [hipaa.md](compliance/hipaa.md) — HIPAA §164.312 mapping
  - [gdpr.md](compliance/gdpr.md) — GDPR Article-level mapping
- [user/COMPLIANCE.md](user/COMPLIANCE.md) — the GDPR + HIPAA operator
  guide (deployment recipes with concrete YAML). Still the canonical
  reference for operationalizing GDPR/HIPAA; the deep-dives above
  extract the framework-vs-adopter split into per-regulation shape.
- [user/OPERATIONS.md § Compliance](user/OPERATIONS.md#compliance-gdpr--hipaa--federal-frameworks)
  — profile → flag mapping table + per-tenant metadata reference
- [FEDERAL-COMPLIANCE-ASSESSMENT-2026-08-06.md](FEDERAL-COMPLIANCE-ASSESSMENT-2026-08-06.md)
  — point-in-time assessment across the 6 federal frameworks
  (referenced by the deep-dives as the source of truth for their
  rating table)

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
- [PIPELINE-STRATEGY.md](dev/pipeline/PIPELINE-STRATEGY.md) — `jaiclaw-pipeline`
  design + DSL
- [PIPELINE-STUDIO-ANALYSIS.md](dev/pipeline/PIPELINE-STUDIO-ANALYSIS.md) —
  **backlog** — visual pipeline-builder UI: gaps, components, baseline
  processor catalog
- [PIPELINE_UX_IMPROVEMENTS.md](dev/pipeline/PIPELINE_UX_IMPROVEMENTS.md) —
  closing notes on the Phase A–F UX overhaul
- [PIPELINE_HOT_RELOAD.md](dev/pipeline/PIPELINE_HOT_RELOAD.md) —
  runtime semantics for hot deploy / undeploy / redeploy (Phase 3 of
  the Pipeline Studio buildout)
- [PIPELINE-STUDIO-PROCESSORS-SPI.md](dev/pipeline/PIPELINE-STUDIO-PROCESSORS-SPI.md) —
  `@PipelineProcessor` + `ConfigurableStageProcessor` SPI
- [TOPICS-PUB-SUB-PLAN.md](dev/TOPICS-PUB-SUB-PLAN.md) —
  **backlog** — cross-channel pub/sub layer with per-tenant topics +
  cross-tenant subscribers
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
