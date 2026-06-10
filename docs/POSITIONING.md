# Where JaiClaw Fits

> **Status:** 0.8.0. The framework is production-deployable today; the
> API surface is governed by the stability program in
> [P3.5](CODEBASE-ANALYSIS-2026-06-10.md) (`@Stable` / `@Experimental` /
> `@Internal` markers). See [ROAD-TO-1.0.md](./ROAD-TO-1.0.md) for what
> changes between now and 1.0.

This document is for technical decision-makers comparing JaiClaw against
LangChain4j, Spring AI alone, Embabel by itself, or rolling your own
LLM-orchestration glue. It's short on purpose: pick the right tool, not
the one with the longest feature list.

---

## What JaiClaw is

JaiClaw is an **embeddable Java agent framework** for Spring Boot
applications. It assembles four things that are usually picked
individually and wired by hand:

1. **LLM orchestration** with the model registry, GOAP planning, and
   structured-output support from [Embabel](https://embabel.com).
2. **Tool calling** with a dual-bridge SPI that interoperates with
   Spring AI's `ToolCallback`, Embabel's tool registry, and the MCP
   protocol — all from one definition.
3. **Multi-channel messaging** with eleven first-party channel adapters
   (Telegram, Slack, Discord, Email, SMS, Signal, Teams, Line, Matrix,
   Google Chat, WebSocket).
4. **Operational concerns** that real production deployments hit on day
   one: multi-tenancy, identity linking, subscription/billing, audit
   trail, internationalization (10 locales), MCP server hosting, JSON-
   file persistence with tenant isolation, security hardening profile.

It is **not** a higher-level RAG library, not a vector-store, not a
prompt-template DSL, and not a low-code agent builder. Those things plug
into JaiClaw rather than competing with it.

---

## The differentiators

Five capabilities are genuinely unique in the Java agent ecosystem as of
mid-2026:

### 1. GOAP planning via Embabel

JaiClaw transparently delegates multi-step planning to Embabel's GOAP
planner when an agent task requires it. The planner sees registered
tools as actions and goals as world-state predicates, producing a
deterministic plan rather than letting the LLM iterate freely. This
trades some prompt-time latency for predictable, auditable behavior —
the exact tradeoff regulated industries ask for first.

LangChain4j has agentic loops. Spring AI has tool calling. Neither ships
a GOAP planner.

### 2. Multi-tenant agent isolation

Tenant resolution, per-tenant configuration, per-tenant skill filtering,
per-tenant storage isolation (in-memory stores, JSON files, async
context propagation), per-tenant chat-model selection, per-tenant
identity linking — all baked into the framework rather than left as
"implement this yourself."

The [multi-tenancy architecture](dev/multi-tenancy-architecture.md)
documents the model in detail. JaiClaw 0.8.0 hardened a series of
isolation gaps surfaced by the [internal audit](CODEBASE-ANALYSIS-2026-06-10.md);
the convention is now "every key carries tenant" — uniformly in SINGLE
and MULTI mode.

### 3. MCP server hosting

JaiClaw is the only Java agent framework that **hosts** Model Context
Protocol (MCP) servers rather than just consuming them. The
`McpToolProvider` and `McpResourceProvider` SPIs let any module expose
tools and resources to any MCP-aware client (Claude Desktop, Claude
Code, other agents) via REST endpoints, SSE, or stdio.

The `jaiclaw-messaging` module exposes channel messaging as 8 MCP tools;
the `jaiclaw-docs` module exposes ~27 documentation files as MCP
resources with full-text search.

### 4. Channel adapter pattern with 11 first-party adapters

Most Java agent frameworks support a chat web UI and a CLI. JaiClaw
ships production-grade adapters for the channels real users live in:
Telegram, Slack, Discord, Email (IMAP/SMTP), SMS (Twilio), Signal,
Teams, Line, Matrix, Google Chat, and WebSocket.

0.8.0 introduced [`AbstractChannelAdapter`](dev/ARCHITECTURE.md#channels)
which collapses the lifecycle, chunking, webhook registration, and
signature verification into a single base class. Adding a new channel
is now a ~200-line exercise rather than a ~600-line one.

### 5. Built-in subscription billing

The `jaiclaw-subscription` extension ships the primitives most agent
deployments need but few frameworks include: subscription tiers, expiry
tracking, per-tier feature flags, per-tier rate limits, and a Telegram-
specific subscription channel for direct in-chat upgrade flows.

Not every deployment needs this, but the ones that do tend to need it
on day 30, not day 300.

---

## When to choose JaiClaw

You should pick JaiClaw if **any two** of the following are true:

- Your team writes Spring Boot for a living and wants to stay in that
  idiom.
- You need agent reach across more than one messaging channel.
- You have multi-tenancy as a real product requirement (not an aspiration).
- You want GOAP-planned deterministic agent behavior, not free-form
  ReAct loops.
- You need to **host** MCP servers, not just consume them.
- You have built-in billing/subscription needs.
- You're deploying into a regulated environment that needs the audit
  trail (`AuditLogger`, `TrajectoryRecorder`, `TranscriptStore`) JaiClaw
  ships.

---

## When to choose something else

### Choose **Spring AI alone** if

- You need chat + simple tool use, no multi-channel reach.
- A single embedding / vector workflow is the whole product.
- You want the smallest possible dependency footprint.

### Choose **LangChain4j** if

- You want the most direct port of LangChain's mental model.
- Your team is more comfortable with imperative chain composition than
  Spring's declarative configuration.
- You don't need multi-channel adapters or MCP server hosting.

### Choose **Embabel by itself** if

- You only need GOAP planning + structured outputs.
- You don't want JaiClaw's broader operational layer.
- You're building a non-Spring Java app.

### Roll your own if

- You're prototyping for under 30 days and your "agent" is two prompt
  templates.
- You explicitly want zero framework lock-in (acceptable trade: you'll
  rewrite the multi-tenancy and channel layers yourself eventually).

---

## What JaiClaw deliberately doesn't do

- **It's not a vector store.** It integrates with Spring AI's
  `VectorStore` SPI; pick the backend that fits your data (Pinecone,
  Chroma, pgvector, in-memory).
- **It's not a prompt-template DSL.** Spring AI's `PromptTemplate` and
  Embabel's structured-output support cover the ground we'd otherwise
  re-invent.
- **It's not a no-code platform.** The audience is Java engineers who
  want a thoughtful framework, not a click-and-drag builder.
- **It doesn't ship a SaaS hosted runtime.** Deploy it yourself —
  helm/k8s manifests in
  [PRODUCTION-DEPLOYMENT.md](user/PRODUCTION-DEPLOYMENT.md).

---

## The "1.0" question

JaiClaw is 0.8.0 today. The stability program ([P3.5](CODEBASE-ANALYSIS-2026-06-10.md))
governs the path to 1.0:

- `@Stable` types and methods will not break between minors after 1.0.
- `@Experimental` types may evolve; we document migration steps in each
  release.
- `@Internal` types are not for consumers; they may change anytime.

JaiClaw 0.8.0 carries `@Stable` markers on the core interfaces an adopter
actually depends on (`ToolCallback`, `ChannelAdapter`, `JaiClawPlugin`,
`AgentRuntime`, `SessionManager`, `TenantContext`, `AuditLogger`,
`McpToolProvider`). See [ROAD-TO-1.0.md](./ROAD-TO-1.0.md) for the
specific gates between here and 1.0.

---

## See also

- [WHAT-IS-JAICLAW.md](user/WHAT-IS-JAICLAW.md) — beginner-friendly
  intro
- [JAICLAW-FROM-PERSONAL-TO-ENTERPRISE.md](user/JAICLAW-FROM-PERSONAL-TO-ENTERPRISE.md)
  — how the same codebase scales from a personal Telegram bot to a
  multi-tenant SaaS
- [ROAD-TO-1.0.md](./ROAD-TO-1.0.md) — release plan + stability gates
- [CODEBASE-ANALYSIS-2026-06-10.md](./CODEBASE-ANALYSIS-2026-06-10.md)
  — internal audit findings that drove 0.8.0
