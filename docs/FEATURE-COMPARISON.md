# Feature Comparison: OpenClaw vs JClaw vs Embabel

## Overview

- **OpenClaw** — TypeScript/Node.js personal AI gateway with 25+ channels, 50+ skills, native apps (macOS/iOS/Android), voice, canvas, browser automation
- **JClaw** — Java 21 / Spring Boot 3.5 / Spring AI port of OpenClaw. Enterprise-focused with multi-tenancy, JWT security, audit, k8s monitoring, modular starters
- **Embabel** — Kotlin/Spring agent framework by Rod Johnson. GOAP planning, workflow patterns, multi-model mixing, structured output, HITL, guardrails, A2A protocol

JClaw's goal is to combine the best of both: OpenClaw's breadth of channels/tools/skills with Embabel's planning/orchestration depth, wrapped in an enterprise-grade Java platform.

---

## 1. OpenClaw vs JClaw (Port Status)

| Feature Area | OpenClaw | JClaw | Gap |
|---|---|---|---|
| **Language** | TypeScript/Node.js | Java 21 / Spring Boot 3.5 | By design |
| **Messaging Channels** | 25+ (WhatsApp, iMessage, Signal, IRC, Twitch, Nostr, LINE, Matrix, Google Chat, etc.) | 6 (Telegram, Slack, Discord, Email, SMS, WebSocket) | 19+ channels missing |
| **LLM Providers** | 20+ (Anthropic, OpenAI, Ollama, Mistral, Bedrock, Qwen, Moonshot, GLM, Venice, LiteLLM, OpenRouter, etc.) | 4 (Anthropic, OpenAI, Gemini, Ollama) | 16+ missing (can add via Spring AI starters) |
| **Built-in Tools** | browser, canvas, exec, file ops, sessions, memory, cron, nodes (camera/screen/location) | 5 core (file read/write, shell exec, web fetch, web search) + 9 k8s | Browser, Canvas, Cron, Device node tools missing |
| **Skills** | 50+ bundled (1password, apple-notes, github, notion, obsidian, spotify, trello, weather, etc.) | 6 bundled (coding, conversation, web-research, system-admin, summarize, k8s-monitoring) | 44+ skills missing |
| **Memory** | Markdown-first files + vector search + auto-compaction + daily logs + long-term curated | In-memory BM25 + VectorStore (Spring AI) | Workspace memory, auto-compaction, daily logs missing |
| **Voice** | Voice Wake, Talk Mode, PTT (macOS/iOS/Android) | None | Voice completely missing |
| **Canvas/A2UI** | Agent-controlled real-time visual UI | None | Canvas completely missing |
| **Native Apps** | macOS menu bar, iOS, Android nodes with camera/screen/location/SMS | None (CLI + gateway only) | Native apps completely missing |
| **Device Nodes** | iOS/Android as sensor nodes (camera snap/clip, screen record, location, contacts, SMS) | None | Device integration missing |
| **Browser Tool** | Dedicated Chrome/Chromium instance with snapshots, actions, JS eval | None | Browser automation missing |
| **Group Chat** | Mention-based routing in groups across channels | None | Group chat routing missing |
| **Multi-Agent** | Multiple isolated agents per gateway, per-agent workspaces, routing | Single agent per runtime | Multi-agent routing missing |
| **Scheduling** | Cron jobs, wakeups, Gmail Pub/Sub, webhooks | None | Scheduling missing |
| **DM Security** | Pairing codes, allowlists, per-channel-peer isolation | JWT auth, tenant isolation | Different approach — JClaw is enterprise-focused |
| **Identity Linking** | Map users across channels to shared sessions | None | Cross-channel identity missing |
| **Context Mgmt** | Token-aware compaction, `/context list/detail`, thinking levels | Basic conversation history | Context compaction, inspection missing |
| **Plugin System** | TypeScript plugins loaded at runtime via jiti, ClawHub marketplace | Java plugins via SPI + Spring scanning + ServiceLoader | JClaw's is more formal but fewer plugins exist |
| **Deployment** | npm, Docker, Podman, Nix, macOS app, iOS, Android, cloud VPS | Maven, Docker, k8s (JKube), Spring Boot starters | JClaw is enterprise-oriented |
| **CLI** | 40+ subcommands, TUI, QR pairing, doctor diagnostics | Spring Shell with ~12 commands + onboarding wizard | Many CLI features missing |
| **Config Hot-Reload** | hybrid/hot/restart modes | Requires restart | Hot-reload missing |
| **Media Pipeline** | FFmpeg transcoding, TTS (ElevenLabs/OpenAI), STT (Whisper), image gen | MediaAnalysisProvider SPI (abstract) | Concrete media processing missing |
| **Session Model** | Main session + per-peer + DM scope policies + thread support + JSONL transcripts | Basic in-memory sessions with tenant isolation | Session continuity, transcripts, DM scoping missing |
| **MCP** | mcporter bridge (loose coupling) | MCP hosting (server-side, REST endpoints) | Different direction — JClaw hosts MCP, OpenClaw consumes it |

---

## 2. JClaw vs Embabel (Framework Comparison)

| Feature Area | JClaw | Embabel | Gap / Synergy |
|---|---|---|---|
| **Planning** | None (sequential ChatClient calls) | GOAP (A* search), Utility AI, State Machine | JClaw needs Embabel's planning |
| **Tool System** | ToolCallback SPI + ToolRegistry + AbstractBuiltinTool | 15+ tool types: Agentic, Playbook, StateMachine, Unfolding, Subagent, MatryoshkaTool, Progressive, Replanning | Embabel far richer |
| **Workflow Patterns** | None | RepeatUntil, ScatterGather, Consensus, Branching, Transformation chains | All missing from JClaw |
| **Human-in-the-Loop** | None | Confirmation dialogs, form-based input, typed requests, conditional awaiting | HITL missing from JClaw |
| **Guardrails** | None | Input/output guardrails, structural validation, path-to-completion validation | Guardrails missing |
| **Blackboard** | None (tool context only) | Typed shared memory, boolean conditions, named bindings, built-in blackboard tools | Shared state missing |
| **LLM Providers** | 4 via Spring AI starters | 11 (Anthropic, OpenAI, Ollama, Bedrock, DeepSeek, Gemini, Google GenAI, Mistral, LM Studio, Docker models) | Embabel has more providers |
| **Multi-Model** | Single provider per runtime | Per-action model selection, role-based mapping, cost-optimized mixing | Model mixing missing from JClaw |
| **Structured Output** | Text generation only | `createObject(prompt, Class)` — JSON-to-Java object mapping | Structured output missing |
| **Subagents** | None | `@RunSubagent`, nested agent invocation, supervisor patterns | Subagent orchestration missing |
| **Event System** | None | 25+ event types (action start/end, goal achieved, LLM calls, tool calls, planning events) | Event system missing |
| **Observability** | GatewayMetrics (counters) + HealthIndicator | OpenTelemetry + Zipkin tracing + instrumented models + event listeners | Observability limited in JClaw |
| **A2A Protocol** | None | Google A2A agent interoperability with `@Export` | A2A missing |
| **MCP** | Server-side MCP hosting | Server + Client MCP support | Complementary |
| **RAG** | Document parsing (PDF/HTML/text) + VectorStore search | Lucene search + Tika parsing + chunking pipeline | Both have RAG; Embabel's is more complete |
| **Forms/UX** | Spring Shell ComponentFlow | 12+ form controls, auto-generation, validation | Rich forms missing from JClaw |
| **Chat System** | Basic session + messages | Full conversation management, windowing, multimodal content | Conversation mgmt richer in Embabel |
| **Testing** | Spock specs (unit tests) | FakeAction, ScriptedLlmOperations, event capture, mock mode | Test utilities missing from JClaw |
| **Persona System** | None | Persona types (CoStar, RoleGoalBackstory), themed logging | Persona system missing |
| **Budget Control** | None | MaxActions, MaxCost, MaxTokens termination policies | Budget control missing |
| **Channels** | 6 messaging channels + gateway | None (no channel concept) | JClaw's strength |
| **Multi-Tenancy** | Full (ThreadLocal tenant, JWT, per-tenant sessions/memory/skills/audit) | Basic (User/UserService) | JClaw's strength |
| **Plugin System** | Plugin SPI + Spring scan + ServiceLoader + HookRunner | @Agent component scanning (agents ARE the extensibility unit) | Different paradigms |
| **Skills** | 6 bundled skills with YAML metadata, tenant filtering, versioning | No separate skill concept (skills = agents) | JClaw's skill system is distinct |
| **Security** | JWT auth, tenant isolation, JJWT | Basic identity management | JClaw's strength |
| **Deployment** | Docker, k8s, Spring Boot starters, gateway app | Spring Boot starters | JClaw has more deployment options |
| **Audit** | AuditEvent + AuditLogger SPI + InMemoryAuditLogger | Event system serves similar purpose | JClaw has formal audit trail |

---

## 3. Three-Way Comparison Summary

| Capability | OpenClaw | JClaw | Embabel | Best Source |
|---|:---:|:---:|:---:|---|
| AI Planning (GOAP) | - | - | +++ | Embabel |
| Workflow Patterns | - | - | +++ | Embabel |
| Multi-Model Mixing | ++ | - | +++ | Embabel |
| Structured Output | + | - | ++ | Embabel |
| Subagent Orchestration | - | - | +++ | Embabel |
| Human-in-the-Loop | - | - | +++ | Embabel |
| Guardrails | + | - | ++ | Embabel |
| Event System | ++ | - | +++ | Embabel |
| Tool Richness | +++ | ++ | +++ | OpenClaw (breadth) / Embabel (depth) |
| Messaging Channels | +++ | ++ | - | OpenClaw |
| Multi-Tenancy | + | +++ | - | JClaw |
| JWT Security | + | +++ | - | JClaw |
| Audit Trail | + | ++ | - | JClaw |
| Voice/PTT | +++ | - | - | OpenClaw |
| Canvas/A2UI | +++ | - | - | OpenClaw |
| Native Apps | +++ | - | - | OpenClaw |
| Browser Automation | +++ | - | - | OpenClaw |
| Device Nodes | +++ | - | - | OpenClaw |
| Memory (workspace) | +++ | + | + | OpenClaw |
| Context Compaction | +++ | - | + | OpenClaw |
| Scheduling/Cron | +++ | - | - | OpenClaw |
| Group Chat Routing | +++ | - | - | OpenClaw |
| Identity Linking | +++ | - | - | OpenClaw |
| Multi-Agent | ++ | - | +++ | Embabel (planning) / OpenClaw (routing) |
| K8s Monitoring | - | +++ | - | JClaw |
| Document Processing | ++ | ++ | ++ | Comparable |
| Plugin System | +++ | ++ | + | OpenClaw |
| Skills | +++ (50+) | ++ (6) | - | OpenClaw |
| MCP | + (client) | ++ (server) | ++ (both) | Embabel |
| A2A Protocol | - | - | ++ | Embabel |
| RAG | + | ++ | ++ | Embabel/JClaw |
| Observability | ++ | + | +++ | Embabel |
| Testing Support | ++ | ++ | +++ | Embabel |
| Enterprise Deployment | + | +++ | ++ | JClaw |
| Spring Integration | - | +++ | +++ | JClaw / Embabel |
| Onboarding UX | ++ | ++ | + | Comparable |
| Forms/Rich UI | + | + | +++ | Embabel |

---

## 4. Strategic Priorities for JClaw

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

### JClaw's Unique Strengths (keep and extend)
1. Enterprise multi-tenancy
2. JWT security
3. Formal audit trail
4. K8s monitoring tools
5. Spring Boot starter ecosystem
6. Clean modular architecture (zero-Spring core)
