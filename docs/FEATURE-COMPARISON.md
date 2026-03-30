# Feature Comparison: OpenClaw vs JaiClaw vs Embabel

## Overview

- **OpenClaw** — TypeScript/Node.js personal AI gateway with 25+ channels, 50+ skills, native apps (macOS/iOS/Android), voice, canvas, browser automation
- **JaiClaw** — Java 21 / Spring Boot 3.5 / Spring AI port of OpenClaw. 40 modules, 8 channel adapters, 30+ tools, enterprise-focused with multi-tenancy, JWT security, audit, k8s monitoring, modular starters
- **Embabel** — Kotlin/Spring agent framework by Rod Johnson. GOAP planning, workflow patterns, multi-model mixing, structured output, HITL, guardrails, A2A protocol

JaiClaw's goal is to combine the best of both: OpenClaw's breadth of channels/tools/skills with Embabel's planning/orchestration depth, wrapped in an enterprise-grade Java platform.

---

## 1. OpenClaw vs JaiClaw (Port Status)

| Feature Area | OpenClaw | JaiClaw | Gap |
|---|---|---|---|
| **Language** | TypeScript/Node.js | Java 21 / Spring Boot 3.5 | By design |
| **Messaging Channels** | 25+ (WhatsApp, iMessage, IRC, Twitch, Nostr, LINE, Matrix, Google Chat, etc.) | 8 (Telegram, Slack, Discord, Email, SMS, Signal, Microsoft Teams, WebSocket) | 17+ channels missing |
| **LLM Providers** | 20+ (Anthropic, OpenAI, Ollama, Mistral, Bedrock, Qwen, Moonshot, GLM, Venice, LiteLLM, OpenRouter, etc.) | 4 (Anthropic, OpenAI, Gemini, Ollama) | 16+ missing (can add via Spring AI starters) |
| **Built-in Tools** | browser, canvas, exec, file ops, sessions, memory, cron, nodes (camera/screen/location) | 5 core + 9 k8s + 8 browser + 3 canvas + 3 code + 8 docstore + 1 memory + 1 security = **38 tools** | Device node tools missing; parity on browser, canvas, code |
| **Skills** | 50+ bundled (1password, apple-notes, github, notion, obsidian, spotify, trello, weather, etc.) | 6 bundled (coding, conversation, web-research, system-admin, summarize, k8s-monitoring) | 44+ skills missing |
| **Memory** | Markdown-first files + vector search + auto-compaction + daily logs + long-term curated | Workspace file memory + daily log appender + hybrid search (BM25 + VectorStore) + session transcript store | **Ported.** Workspace memory, daily logs, hybrid search all implemented |
| **Voice** | Voice Wake, Talk Mode, PTT (macOS/iOS/Android) | TTS/STT SPI + OpenAI provider (TTS + Whisper STT) + directive parser | **Ported (API layer).** Native app push-to-talk missing |
| **Canvas/A2UI** | Agent-controlled real-time visual UI | 3 tools: `canvas_present` (push HTML), `canvas_eval` (run JS), `canvas_snapshot` (capture state) | **Ported.** Full A2UI artifact rendering |
| **Native Apps** | macOS menu bar, iOS, Android nodes with camera/screen/location/SMS | None (CLI + gateway only) | Native apps completely missing |
| **Device Nodes** | iOS/Android as sensor nodes (camera snap/clip, screen record, location, contacts, SMS) | None | Device integration missing |
| **Browser Tool** | Dedicated Chrome/Chromium instance with snapshots, actions, JS eval | 8 Playwright tools: navigate, click, type, screenshot, evaluate, read_page, list_tabs, close_tab | **Ported.** Full browser automation |
| **Group Chat** | Mention-based routing in groups across channels | None | Group chat routing missing |
| **Multi-Agent** | Multiple isolated agents per gateway, per-agent workspaces, routing | Single agent per runtime | Multi-agent routing missing |
| **Scheduling** | Cron jobs, wakeups, Gmail Pub/Sub, webhooks | `CronService` — cron expressions, JSON persistence, virtual threads, run history, manual trigger | **Ported.** Full cron scheduling |
| **DM Security** | Pairing codes, allowlists, per-channel-peer isolation | JWT auth, tenant isolation, agent-to-agent security handshake (ECDH + challenge-response) | Different approach — JaiClaw is enterprise-focused + has agent-to-agent crypto |
| **Identity Linking** | Map users across channels to shared sessions | `IdentityLinkService` — cross-channel canonical user IDs, link/unlink/resolve | **Ported.** Full cross-channel identity |
| **Context Mgmt** | Token-aware compaction, `/context list/detail`, thinking levels | `CompactionService` — token-budget-aware LLM summarization, identifier preservation | **Ported.** Context compaction implemented; `/context` inspection missing |
| **Plugin System** | TypeScript plugins loaded at runtime via jiti, ClawHub marketplace | Java plugins via SPI + Spring scanning + ServiceLoader + lifecycle hooks (BEFORE/AFTER_TOOL_CALL, MESSAGE_RECEIVED, etc.) | JaiClaw's is more formal but fewer plugins exist |
| **Deployment** | npm, Docker, Podman, Nix, macOS app, iOS, Android, cloud VPS | Maven, Docker, k8s (JKube), Spring Boot starters (9 starters) | JaiClaw is enterprise-oriented |
| **CLI** | 40+ subcommands, TUI, QR pairing, doctor diagnostics | Spring Shell with ~12 commands + onboarding wizard | Many CLI features missing |
| **Config Hot-Reload** | hybrid/hot/restart modes | Requires restart | Hot-reload missing |
| **Media Pipeline** | FFmpeg transcoding, TTS (ElevenLabs/OpenAI), STT (Whisper), image gen | `MediaAnalysisProvider` SPI + `CompositeMediaAnalyzer` + Voice module (OpenAI TTS/STT) | **Partial.** TTS/STT ported, FFmpeg transcoding and image gen missing |
| **Session Model** | Main session + per-peer + DM scope policies + thread support + JSONL transcripts | Sessions with tenant isolation + `SessionTranscriptStore` for persistent transcripts | **Partial.** Transcripts ported; DM scoping, thread support missing |
| **MCP** | mcporter bridge (loose coupling) | MCP hosting (REST + SSE + stdio transports) + Security Handshake + Messaging MCP provider (8 channel tools) | Different direction — JaiClaw hosts MCP, OpenClaw consumes it |
| **Document Store** | N/A | `DocStoreService` — 8 tools (search, list, get, tag, describe, analyze, delete, add URL) + LLM analysis + Telegram integration | **JaiClaw-original feature** |
| **Code Tools** | N/A | 3 tools: `file_edit`, `glob`, `grep` — agentic coding capabilities | **JaiClaw-original feature** |
| **Agent-to-Agent Security** | N/A | 31-class cryptographic handshake protocol (ECDH, challenge-response, pluggable bootstrap trust, MCP endpoint) | **JaiClaw-original feature** |
| **Explicit Tool Loop** | N/A | `ExplicitToolLoop` — iteration capping, hook observability, human-in-the-loop approval gates | **JaiClaw-original feature** |

---

## 2. JaiClaw vs Embabel (Framework Comparison)

| Feature Area | JaiClaw | Embabel | Gap / Synergy |
|---|---|---|---|
| **Planning** | None (sequential ChatClient calls) | GOAP (A* search), Utility AI, State Machine | JaiClaw needs Embabel's planning |
| **Tool System** | ToolCallback SPI + ToolRegistry + 38 tools across 10 categories + ExplicitToolLoop with approval gates | 15+ tool types: Agentic, Playbook, StateMachine, Unfolding, Subagent, MatryoshkaTool, Progressive, Replanning | Embabel richer in tool types; JaiClaw richer in concrete tools |
| **Workflow Patterns** | ExplicitToolLoop with iteration capping and hook observability | RepeatUntil, ScatterGather, Consensus, Branching, Transformation chains | JaiClaw has basic loop; Embabel has advanced patterns |
| **Human-in-the-Loop** | ExplicitToolLoop approval gate (approve/deny/modify per tool call) | Confirmation dialogs, form-based input, typed requests, conditional awaiting | JaiClaw has basic HITL; Embabel's is richer |
| **Guardrails** | None | Input/output guardrails, structural validation, path-to-completion validation | Guardrails missing |
| **Blackboard** | None (tool context only) | Typed shared memory, boolean conditions, named bindings, built-in blackboard tools | Shared state missing |
| **LLM Providers** | 4 via Spring AI starters | 11 (Anthropic, OpenAI, Ollama, Bedrock, DeepSeek, Gemini, Google GenAI, Mistral, LM Studio, Docker models) | Embabel has more providers |
| **Multi-Model** | Single provider per runtime | Per-action model selection, role-based mapping, cost-optimized mixing | Model mixing missing from JaiClaw |
| **Structured Output** | Text generation only | `createObject(prompt, Class)` — JSON-to-Java object mapping | Structured output missing |
| **Subagents** | None | `@RunSubagent`, nested agent invocation, supervisor patterns | Subagent orchestration missing |
| **Event System** | None | 25+ event types (action start/end, goal achieved, LLM calls, tool calls, planning events) | Event system missing |
| **Observability** | GatewayMetrics (counters) + HealthIndicator | OpenTelemetry + Zipkin tracing + instrumented models + event listeners | Observability limited in JaiClaw |
| **A2A Protocol** | None | Google A2A agent interoperability with `@Export` | A2A missing |
| **MCP** | Server-side MCP hosting + Security Handshake MCP provider | Server + Client MCP support | Complementary; JaiClaw adds agent-to-agent security |
| **RAG** | Document parsing (PDF/HTML/text) + VectorStore search + DocStore (8-tool document management) | Lucene search + Tika parsing + chunking pipeline | Both have RAG; JaiClaw's DocStore adds document lifecycle management |
| **Forms/UX** | Spring Shell ComponentFlow | 12+ form controls, auto-generation, validation | Rich forms missing from JaiClaw |
| **Chat System** | Sessions with compaction, transcript storage, identity linking | Full conversation management, windowing, multimodal content | JaiClaw improved; Embabel still richer |
| **Testing** | Spock specs (unit tests) | FakeAction, ScriptedLlmOperations, event capture, mock mode | Test utilities missing from JaiClaw |
| **Persona System** | None | Persona types (CoStar, RoleGoalBackstory), themed logging | Persona system missing |
| **Budget Control** | None | MaxActions, MaxCost, MaxTokens termination policies | Budget control missing |
| **Channels** | 8 messaging channels (Telegram, Slack, Discord, Email, SMS, Signal, Teams, WebSocket) + gateway | None (no channel concept) | JaiClaw's strength |
| **Multi-Tenancy** | Full (ThreadLocal tenant, JWT, per-tenant sessions/memory/skills/audit) | Basic (User/UserService) | JaiClaw's strength |
| **Plugin System** | Plugin SPI + Spring scan + ServiceLoader + HookRunner (BEFORE/AFTER_TOOL_CALL, MESSAGE_RECEIVED, etc.) | @Agent component scanning (agents ARE the extensibility unit) | Different paradigms |
| **Skills** | 6 bundled skills with YAML metadata, tenant filtering, versioning | No separate skill concept (skills = agents) | JaiClaw's skill system is distinct |
| **Security** | JWT auth, tenant isolation, JJWT + agent-to-agent crypto handshake (ECDH, challenge-response, MCP endpoint) | Basic identity management | JaiClaw's strength (enterprise + agent-to-agent) |
| **Browser** | 8 Playwright tools (navigate, click, type, screenshot, evaluate, read_page, tabs) | None | JaiClaw's strength |
| **Voice** | TTS/STT SPI + OpenAI provider | None | JaiClaw's strength |
| **Canvas** | 3 A2UI tools (present HTML, eval JS, snapshot) | None | JaiClaw's strength |
| **Cron/Scheduling** | CronService — cron expressions, JSON persistence, virtual threads, run history | None | JaiClaw's strength |
| **Identity** | Cross-channel identity linking (canonical user IDs) | None | JaiClaw's strength |
| **Context Compaction** | CompactionService — token-budget-aware LLM summarization | None | JaiClaw's strength |
| **Deployment** | Docker, k8s, Spring Boot starters (9), gateway app, CLI shell | Spring Boot starters | JaiClaw has more deployment options |
| **Audit** | AuditEvent + AuditLogger SPI + InMemoryAuditLogger | Event system serves similar purpose | JaiClaw has formal audit trail |

---

## 3. Three-Way Comparison Summary

| Capability | OpenClaw | JaiClaw | Embabel | Best Source |
|---|:---:|:---:|:---:|---|
| AI Planning (GOAP) | - | - | +++ | Embabel |
| Workflow Patterns | - | + | +++ | Embabel |
| Multi-Model Mixing | ++ | - | +++ | Embabel |
| Structured Output | + | - | ++ | Embabel |
| Subagent Orchestration | - | - | +++ | Embabel |
| Human-in-the-Loop | - | + | +++ | Embabel (JaiClaw has basic approval gates) |
| Guardrails | + | - | ++ | Embabel |
| Event System | ++ | - | +++ | Embabel |
| Tool Richness | +++ | +++ (38 tools) | +++ | All three strong; different strengths |
| Messaging Channels | +++ (25+) | ++ (8) | - | OpenClaw |
| Multi-Tenancy | + | +++ | - | JaiClaw |
| JWT Security | + | +++ | - | JaiClaw |
| Agent-to-Agent Security | - | +++ (ECDH handshake) | - | JaiClaw |
| Audit Trail | + | ++ | - | JaiClaw |
| Voice/PTT | +++ | ++ (TTS/STT SPI + OpenAI) | - | OpenClaw (native); JaiClaw (API) |
| Canvas/A2UI | +++ | ++ (3 tools) | - | OpenClaw (JaiClaw has parity) |
| Native Apps | +++ | - | - | OpenClaw |
| Browser Automation | +++ | +++ (8 Playwright tools) | - | OpenClaw / JaiClaw (parity) |
| Device Nodes | +++ | - | - | OpenClaw |
| Memory (workspace) | +++ | +++ (workspace + daily logs + hybrid search + transcripts) | + | OpenClaw / JaiClaw (parity) |
| Context Compaction | +++ | ++ (LLM summarization) | + | OpenClaw / JaiClaw (near parity) |
| Scheduling/Cron | +++ | +++ (CronService + JSON store + virtual threads) | - | OpenClaw / JaiClaw (parity) |
| Group Chat Routing | +++ | - | - | OpenClaw |
| Identity Linking | +++ | ++ (IdentityLinkService) | - | OpenClaw / JaiClaw (parity) |
| Multi-Agent | ++ | - | +++ | Embabel (planning) / OpenClaw (routing) |
| K8s Monitoring | - | +++ (9 tools) | - | JaiClaw |
| Document Processing | ++ | ++ | ++ | Comparable |
| Document Store | - | +++ (8 tools + LLM analysis + Telegram integration) | - | JaiClaw |
| Code Tools | - | ++ (file_edit, glob, grep) | - | JaiClaw |
| Plugin System | +++ | ++ | + | OpenClaw |
| Skills | +++ (50+) | ++ (6) | - | OpenClaw |
| MCP | + (client) | +++ (server: REST + SSE + stdio + security handshake + messaging tools) | ++ (both) | JaiClaw / Embabel |
| A2A Protocol | - | - | ++ | Embabel |
| RAG | + | ++ | ++ | Embabel/JaiClaw |
| Observability | ++ | + | +++ | Embabel |
| Testing Support | ++ | ++ | +++ | Embabel |
| Enterprise Deployment | + | +++ (9 starters) | ++ | JaiClaw |
| Spring Integration | - | +++ | +++ | JaiClaw / Embabel |
| Onboarding UX | ++ | ++ | + | Comparable |
| Forms/Rich UI | + | + | +++ | Embabel |

---

## 4. Strategic Priorities for JaiClaw

### From Embabel (activate the bridge)
1. GOAP planning — the core differentiator
2. Workflow patterns (RepeatUntil, ScatterGather, Consensus)
3. Multi-model mixing per action
4. Structured output (`createObject`)
5. Human-in-the-loop
6. Guardrails
7. Budget/cost control
8. Event system + OpenTelemetry observability

### From OpenClaw (port key features)
1. More channels (WhatsApp, iMessage, Signal at minimum)
2. Context compaction (session summarization near token limits)
3. Markdown workspace memory (daily logs + long-term curated)
4. Browser tool (Playwright for Java)
5. Scheduling/cron
6. Group chat routing with @mention activation
7. Identity linking across channels
8. More skills (port the most popular ones)
9. Voice capabilities (if native apps are in scope)

### JaiClaw's Unique Strengths (keep and extend)
1. Enterprise multi-tenancy
2. JWT security
3. Formal audit trail
4. K8s monitoring tools
5. Spring Boot starter ecosystem
6. Clean modular architecture (zero-Spring core)
