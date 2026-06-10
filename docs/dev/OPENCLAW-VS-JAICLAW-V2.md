# OpenClaw vs JaiClaw: Feature Comparison v2

> **Date:** 2026-03-30
> **Related:** `docs/OPENCLAW-PARITY-PLAN-V2.md` (implementation plan for gaps)

## Overview

| | OpenClaw | JaiClaw |
|---|---|---|
| **Language** | TypeScript / Node.js | Java 21 / Spring Boot 3.5 / Spring AI 1.1 |
| **Architecture** | Monorepo with bundled workspace plugins | Maven multi-module (42 modules, 7-layer architecture) |
| **Primary audience** | Power users, self-hosters, personal AI gateway | Enterprise, SaaS platforms, Java teams |
| **Module count** | ~1 core + 80+ bundled plugins | 42 Maven modules + 11 starters + 17 examples |
| **License** | Open source | MIT (commercial licensing for SaaS/enterprise) |

---

## Messaging Channels

| Channel | OpenClaw | JaiClaw | Notes |
|---------|:---:|:---:|-------|
| Telegram | Yes | Yes | Both support bot API polling + webhooks |
| Slack | Yes | Yes | OpenClaw: Events API. JaiClaw: Socket Mode |
| Discord | Yes | Yes | Both support Gateway + Interactions |
| Email (IMAP/SMTP) | Yes | Yes | Both support inbound polling + outbound SMTP |
| SMS (Twilio) | Yes | Yes | Both support webhook inbound + REST outbound |
| Signal | Yes | Yes | |
| Microsoft Teams | Yes | Yes | JaiClaw has Adaptive Cards support |
| WhatsApp | Yes | **No** | OpenClaw uses whatsapp-web.js |
| iMessage | Yes | **No** | macOS-only, AppleScript bridge |
| BlueBubbles | Yes | **No** | Alternative iMessage bridge |
| Matrix | Yes | **No** | Open protocol, matrix-js-sdk |
| LINE | Yes | **No** | Large APAC user base |
| Google Chat | Yes | **No** | Google Workspace integration |
| Mattermost | Yes | **No** | Self-hosted Slack alternative |
| IRC | Yes | **No** | |
| Twitch | Yes | **No** | Streaming/entertainment |
| Nostr | Yes | **No** | Decentralized protocol |
| Tlon (Landscape) | Yes | **No** | |
| Feishu | Yes | **No** | Chinese enterprise messaging |
| Zalo | Yes | **No** | Vietnamese messaging |
| Zalo Personal | Yes | **No** | |
| WeChat | Yes | **No** | Chinese ecosystem |
| Synology Chat | Yes | **No** | NAS-integrated |
| Nextcloud Talk | Yes | **No** | Self-hosted |
| WebSocket (direct) | Yes | Yes | Both expose WebSocket for custom clients |
| **Total** | **25+** | **7 + WebSocket** | |

---

## LLM / Model Providers

| Provider | OpenClaw | JaiClaw | Notes |
|----------|:---:|:---:|-------|
| Anthropic (Claude) | Yes | Yes | Primary provider for both |
| OpenAI (GPT) | Yes | Yes | |
| Google Gemini | Yes | Yes | JaiClaw has dedicated starter |
| Ollama (local) | Yes | Yes | JaiClaw has dedicated starter |
| Groq | Yes | **No** | Fast inference |
| Mistral | Yes | **No** | EU-based |
| DeepSeek | Yes | **No** | Cost-effective |
| xAI (Grok) | Yes | **No** | |
| Perplexity | Yes | **No** | Search-augmented |
| Together | Yes | **No** | |
| Venice | Yes | **No** | Privacy-focused |
| OpenRouter | Yes | **No** | Meta-gateway to 100+ models |
| LiteLLM | Yes | **No** | Proxy/gateway |
| Hugging Face | Yes | **No** | |
| vLLM | Yes | **No** | Self-hosted inference |
| SGLang | Yes | **No** | Self-hosted inference |
| BytePlus (Doubao) | Yes | **No** | Chinese market |
| Qianfan (Baidu) | Yes | **No** | Chinese market |
| Kimi / Moonshot | Yes | **No** | Chinese market |
| ModelStudio | Yes | **No** | |
| NVIDIA | Yes | **No** | |
| Cloudflare AI Gateway | Yes | **No** | CDN-edge inference |
| Vercel AI Gateway | Yes | **No** | |
| **Total** | **20+** | **4** | Many are OpenAI-compatible, easing porting |

---

## Agent Tools

| Tool Category | OpenClaw | JaiClaw | Notes |
|---------------|:---:|:---:|-------|
| **File Read/Write** | Yes | Yes | JaiClaw has `FileReadTool`, `FileWriteTool` |
| **Shell Execution** | Yes | Yes | JaiClaw has `ShellExecTool` + `WhitelistedCommandTool` |
| **Web Fetch** | Yes | Yes | Both have URL fetch with caching |
| **Web Search** | Yes | Yes | OpenClaw: 7+ providers. JaiClaw: generic + Perplexity CLI |
| **Browser Automation** | Yes | Yes | Both use Playwright (8 tools each) |
| **Canvas / A2UI** | Yes | Yes | Both have present/eval/snapshot tools |
| **Code Editing** | Yes | Yes | JaiClaw has `file_edit`, `glob`, `grep` |
| **Cron / Scheduling** | Yes | Yes | Both support dynamic cron job management |
| **Memory Save/Recall** | Yes | Yes | Both have workspace memory + vector search |
| **TTS / STT** | Yes | Yes | OpenClaw: ElevenLabs + Deepgram. JaiClaw: OpenAI |
| **Image Generation** | Yes | **No** | OpenClaw: DALL-E + Stable Diffusion (FAL) |
| **Session Spawn** | Yes | **No** | OpenClaw agents can create sub-sessions |
| **Session Yield** | Yes | **No** | Interactive pause/resume |
| **Session History** | Yes | Partial | OpenClaw has richer session inspection tools |
| **Device Nodes** | Yes | **No** | Camera, screen, location, contacts on paired devices |
| **Message Send** | Yes | Yes | Both can send messages to channels programmatically |
| **Agent List/Switch** | Yes | **No** | OpenClaw supports multi-agent workspaces |
| **K8s Operations** | **No** | Yes | JaiClaw-original: 9 Kubernetes tools |
| **DocStore** | **No** | Yes | JaiClaw-original: 8 document management tools |
| **Security Handshake** | **No** | Yes | JaiClaw-original: agent-to-agent ECDH crypto |
| **Claude CLI Bridge** | **No** | Yes | JaiClaw-original: delegate to OpenClaw CLI |

---

## Skills

| | OpenClaw | JaiClaw |
|---|---|---|
| **Bundled skills** | 50+ | 6 |
| **Skill format** | Markdown + YAML frontmatter | Markdown + YAML (compatible format) |
| **Per-tenant filtering** | No (per-agent) | Yes |
| **Versioning** | No | Yes (semantic versioning) |
| **Examples** | 1password, apple-notes, github, notion, obsidian, spotify, trello, weather, etc. | coding, conversation, web-research, system-admin, summarize, k8s-monitoring |

---

## Plugin System

| Aspect | OpenClaw | JaiClaw |
|--------|----------|---------|
| **Plugin SDK** | Published npm packages (`openclaw/plugin-sdk/*`) | Java SPI + Spring component scanning + ServiceLoader |
| **Plugin types** | Channel, Provider, Tool, Setup | Plugin (generic with hook-based specialization) |
| **Discovery** | Runtime jiti loader + ClawHub marketplace | ServiceLoader + `@Component` scanning + explicit registration |
| **Hook system** | Plugin lifecycle hooks | `HookName` enum: onSessionStart, onMessageReceived, onToolCall, onSessionEnd, etc. |
| **Third-party ecosystem** | Active (plugins in the wild) | Nascent (no external plugins yet) |
| **Bundled plugins** | 80+ | ~20 (extensions/ directory) |
| **Plugin isolation** | Process-level (Node worker) | Classpath (shared ClassLoader) |
| **Hot-loading** | Yes | No (requires restart) |

---

## Gateway & Routing

| Feature | OpenClaw | JaiClaw | Notes |
|---------|:---:|:---:|-------|
| REST API | Yes | Yes | JaiClaw: `/api/chat`, `/api/health`, `/api/channels` |
| WebSocket streaming | Yes | Yes | Both support real-time session streaming |
| Webhook ingress | Yes | Yes | JaiClaw: unified `/webhook/{channel}` |
| MCP server | Yes | Yes | JaiClaw hosts MCP (REST + SSE + stdio). OpenClaw consumes MCP. |
| Multi-agent routing | Yes | **No** | OpenClaw: multiple agents per gateway with routing rules |
| Group chat routing | Yes | **No** | OpenClaw: @mention activation, reply-back mode |
| Config hot-reload | Yes | **No** | OpenClaw: file watcher, no restart needed |
| Channel health probing | Yes | **No** | OpenClaw: `channels status --probe` |
| Message debouncing | Yes | **No** | OpenClaw coalesces rapid-fire messages |

---

## Session Management

| Feature | OpenClaw | JaiClaw | Notes |
|---------|:---:|:---:|-------|
| Per-user sessions | Yes | Yes | Both isolate by user/channel |
| Session persistence | Yes (JSONL) | Yes (JSONL) | |
| Session types | Main, Group, Cron, Hook, Spawned | Single type | OpenClaw has richer session taxonomy |
| Session spawning | Yes | **No** | Create child sessions for parallel work |
| Session yielding | Yes | **No** | Pause and await user input |
| Queue modes | Concurrent + Sequential | Sequential only | |
| Context compaction | Yes | Yes | Both use LLM summarization with identifier preservation |
| Session key convention | `{channel}:{account}:{peer}` | `{agentId}:{channel}:{accountId}:{peerId}` | JaiClaw includes agentId |
| Multi-tenant isolation | Per-agent | Per-tenant | Different isolation model |

---

## Memory & Context

| Feature | OpenClaw | JaiClaw | Notes |
|---------|:---:|:---:|-------|
| Workspace memory | Yes (Markdown files) | Yes (Markdown files) | Parity |
| Daily logs | Yes | Yes | Parity |
| Vector search | Yes (LanceDB) | Yes (Spring AI VectorStore) | Different backends |
| Hybrid search | Yes | Yes (BM25 + vector) | |
| Session transcripts | Yes (JSONL) | Yes (JSONL) | Parity |
| Context compaction | Yes | Yes | Both use LLM summarization |
| Long-term curated memory | Yes | Partial | OpenClaw has more sophisticated curation |

---

## Security

| Feature | OpenClaw | JaiClaw | Notes |
|---------|:---:|:---:|-------|
| API key auth | Yes | Yes | |
| JWT auth | Yes | Yes | JaiClaw: HMAC validation with role extraction |
| DM pairing (unknown sender approval) | Yes | **No** | OpenClaw: pairing codes, allowlists |
| Per-channel DM policies | Yes | **No** | Open / Pairing / Blocked per channel |
| Account allowlists | Yes | **No** | |
| Mention gating | Yes | **No** | |
| Command gating | Yes | **No** | |
| Execution approvals | Yes | Partial | JaiClaw has ExplicitToolLoop approval gate but no channel-delivered UX |
| Rate limiting | Yes | Yes | |
| Audit logging | Partial | Yes | JaiClaw has formal `AuditLogger` SPI |
| Agent-to-agent security | **No** | Yes | JaiClaw-original: ECDH + challenge-response handshake |
| Multi-tenancy | Basic | Full | JaiClaw: TenantGuard, per-tenant isolation across all layers |
| SSRF protection | Yes | **No** | OpenClaw validates URLs in web tools |
| Sandbox boundaries | Yes | Partial | OpenClaw: filesystem + process sandboxing |

---

## Voice & Audio

| Feature | OpenClaw | JaiClaw | Notes |
|---------|:---:|:---:|-------|
| TTS (text-to-speech) | Yes (ElevenLabs + OpenAI + system) | Yes (OpenAI) | OpenClaw has more providers |
| STT (speech-to-text) | Yes (Deepgram + OpenAI) | Yes (OpenAI) | OpenClaw has more providers |
| Voice Wake (wake word) | Yes (macOS/iOS) | **No** | Requires native app |
| Talk Mode (continuous) | Yes (Android) | **No** | Requires native app |
| Voice Call plugin | Yes | **No** | |
| Voice overlay (macOS) | Yes | **No** | Requires native app |
| TTS directive parser | Yes | Yes | Both parse inline voice markup |

---

## Media Pipeline

| Feature | OpenClaw | JaiClaw | Notes |
|---------|:---:|:---:|-------|
| Image analysis | Yes | Yes | Both use LLM vision |
| Audio processing | Yes | Yes (via voice module) | |
| Video handling | Yes | Partial | |
| FFmpeg transcoding | Yes | **No** | |
| Image generation | Yes (DALL-E + FAL) | **No** | |
| Size caps / compression | Yes | **No** | Per-channel media limits |
| Temp file lifecycle | Yes | **No** | Auto-cleanup of media files |
| Media sanitization | Yes (EXIF strip) | **No** | |
| Image format conversion | Yes | **No** | |

---

## Native Apps & Companion Platforms

| Platform | OpenClaw | JaiClaw | Notes |
|----------|:---:|:---:|-------|
| macOS menu bar app | Yes | **No** | Status, quick chat, gateway control |
| macOS canvas host | Yes | **No** | A2UI rendering |
| macOS voice overlay | Yes | **No** | |
| iOS app | Yes | **No** | Push notifications, device control |
| Android app | Yes | **No** | Talk Mode, device control |
| CLI / Shell | Yes (40+ commands) | Yes (~12 commands) | |
| Web dashboard | Yes | **No** | |
| Device node control | Yes | **No** | Camera, screen, location on paired devices |

---

## Configuration & Operations

| Feature | OpenClaw | JaiClaw | Notes |
|---------|:---:|:---:|-------|
| Config format | YAML | YAML (Spring Boot) | |
| Config hot-reload | Yes | **No** | |
| Doctor diagnostics | Yes | **No** | Validate config, probe channels, check auth |
| Backup / restore | Yes | **No** | |
| Interactive onboarding | Rich (multi-step wizard) | Basic (`onboard` command) | |
| Environment variable overrides | Yes | Yes | |
| Per-agent workspaces | Yes | Partial (per-tenant) | |
| Spring Boot starters | **No** | Yes (11 starters) | JaiClaw strength |
| Docker / k8s deploy | Yes | Yes (JKube) | |
| Auto-updates (Sparkle) | Yes (macOS) | **No** | |

---

## Testing & Development

| Feature | OpenClaw | JaiClaw | Notes |
|---------|:---:|:---:|-------|
| Test framework | Vitest | Spock (Groovy) | |
| Coverage thresholds | 70% (V8) | Configurable | |
| Test count | Large suite | 63+ Spock specs | |
| E2E tests | Yes | Partial | |
| Live model tests | Yes | **No** | |
| Docker test environments | Yes | Yes | |
| Example applications | **No** | Yes (17 examples) | JaiClaw strength |
| Maven archetypes | **No** | Planned | JaiClaw roadmap |

---

## Deployment & Distribution

| Method | OpenClaw | JaiClaw | Notes |
|--------|:---:|:---:|-------|
| npm install | Yes | **No** | |
| Maven / Gradle dependency | **No** | Yes | JaiClaw is embeddable |
| Docker images | Yes | Yes | |
| Kubernetes (Helm/JKube) | Yes | Yes | |
| macOS app (DMG) | Yes | **No** | |
| iOS App Store | Yes | **No** | |
| Android (APK/Play Store) | Yes | **No** | |
| Spring Boot starter | **No** | Yes (11 starters) | One-line dependency |
| Raspberry Pi | Yes | Yes (via Docker/JRE) | |
| VPS / Cloud | Yes | Yes | |

---

## Document Processing

| Feature | OpenClaw | JaiClaw | Notes |
|---------|:---:|:---:|-------|
| PDF parsing | Yes | Yes (pdfbox) | |
| HTML parsing | Yes | Yes (jsoup) | |
| Plain text | Yes | Yes | |
| Document chunking | Partial | Yes (configurable strategies) | |
| Document store (CRUD) | **No** | Yes (8 tools) | JaiClaw-original |
| Telegram upload-to-store | **No** | Yes | JaiClaw-original |
| Semantic search over docs | Partial | Yes (vector + full-text) | |

---

## Scheduling & Automation

| Feature | OpenClaw | JaiClaw | Notes |
|---------|:---:|:---:|-------|
| Cron expressions | Yes | Yes (5-field) | Parity |
| Dynamic job creation | Yes | Yes | Both allow agents to create cron jobs |
| Job persistence | Yes | Yes (JSON file + H2) | |
| Timezone support | Yes | Yes | |
| Run history | Yes | Yes | |
| Virtual thread execution | N/A (Node event loop) | Yes | |
| Cron manager sidecar | **No** | Yes | JaiClaw can run cron as separate process |
| Webhook triggers | Yes | Partial | |
| Gmail Pub/Sub | Yes | **No** | |

---

## Unique to OpenClaw

Features with no JaiClaw equivalent:

1. **17+ additional messaging channels** (WhatsApp, iMessage, Matrix, LINE, etc.)
2. **16+ additional LLM providers** (Groq, Mistral, DeepSeek, OpenRouter, etc.)
3. **Native companion apps** (macOS menu bar, iOS, Android)
4. **Voice Wake** (wake-word activation on macOS/iOS)
5. **Talk Mode** (continuous voice on Android)
6. **Image generation** (DALL-E, Stable Diffusion via FAL)
7. **Full media pipeline** (FFmpeg, size caps, sanitization, temp lifecycle)
8. **DM security & pairing** (unknown sender approval, allowlists, policies)
9. **Multi-agent routing** (multiple agents per gateway with routing rules)
10. **Group chat routing** (@mention activation, reply-back mode)
11. **Session spawning & sub-agents** (parallel child sessions)
12. **Config hot-reload** (file watcher, no restart)
13. **Doctor diagnostics** (config validation, channel probing)
14. **Extended thinking** (per-session thinking config, streaming thought output)
15. **Device node control** (camera, screen, location on paired devices)
16. **Web search provider diversity** (Brave, DuckDuckGo, Tavily, Exa, Firecrawl, etc.)
17. **50+ bundled skills** (GitHub, Notion, Obsidian, Spotify, etc.)
18. **Plugin marketplace** (ClawHub, third-party plugins)
19. **SSRF protection** in web tools
20. **Message debouncing/coalescing**

---

## Unique to JaiClaw

Features with no OpenClaw equivalent:

1. **Enterprise multi-tenancy** (TenantGuard, per-tenant isolation across all persistence layers)
2. **Agent-to-agent security** (ECDH + challenge-response handshake, 31 classes)
3. **Kubernetes monitoring tools** (9 k8s tools via Fabric8)
4. **Document store** (8-tool document lifecycle management with LLM analysis)
5. **Spring Boot starters** (11 one-line dependency modules for quick setup)
6. **Embabel Agent bridge** (GOAP planning, workflow patterns integration)
7. **Explicit tool loop** (iteration capping, hook observability, approval gates)
8. **Formal audit trail** (`AuditLogger` SPI with structured events)
9. **Role-based tool profiles** (FULL, READONLY, MINIMAL tool filtering)
10. **Skill versioning** (semantic versioning with per-tenant filtering)
11. **Subscription / payment system** (Stripe, PayPal, Telegram Stars)
12. **Cron manager sidecar** (standalone cron scheduler process)
13. **Code tools** (`file_edit` with range editing, `glob`, `grep`)
14. **Claude CLI bridge tool** (delegate to OpenClaw CLI from Java agent)
15. **MCP server hosting** (REST + SSE + stdio transports with security)
16. **17 example applications** (code-review, helpdesk, incident-responder, etc.)
17. **Standalone CLI tools** (Perplexity CLI, REST CLI architect, skill creator)
18. **JWT + API key dual auth** with composite tenant resolution
19. **Per-tenant LLM model selection** (`TenantChatModelFactory`)
20. **Virtual thread execution** throughout async operations

---

## Summary Scorecard

| Dimension | OpenClaw | JaiClaw | Winner |
|-----------|:---:|:---:|--------|
| Channel breadth | +++++ | ++ | OpenClaw |
| Provider breadth | +++++ | ++ | OpenClaw |
| Tool richness | ++++ | ++++ | Tie (different strengths) |
| Skill library | +++++ | + | OpenClaw |
| Plugin ecosystem | +++++ | ++ | OpenClaw |
| Native apps / UX | +++++ | — | OpenClaw |
| Voice capabilities | ++++ | ++ | OpenClaw |
| Media pipeline | ++++ | + | OpenClaw |
| Group / multi-agent | ++++ | — | OpenClaw |
| DM security | ++++ | + | OpenClaw |
| Enterprise multi-tenancy | + | +++++ | JaiClaw |
| JWT / auth security | ++ | ++++ | JaiClaw |
| Audit & compliance | + | ++++ | JaiClaw |
| Agent-to-agent crypto | — | +++++ | JaiClaw |
| K8s / cloud-native | + | ++++ | JaiClaw |
| Spring ecosystem | — | +++++ | JaiClaw |
| Embeddability | + | +++++ | JaiClaw |
| Document management | + | ++++ | JaiClaw |
| Example apps | — | ++++ | JaiClaw |
| Code architecture | +++ | ++++ | JaiClaw (7-layer, zero-Spring core) |
| Testing infrastructure | +++ | +++ | Tie |
| Config hot-reload | ++++ | — | OpenClaw |
| Diagnostics / doctor | ++++ | + | OpenClaw |
| Onboarding UX | +++ | ++ | OpenClaw |

**Bottom line:** OpenClaw leads in breadth (channels, providers, skills, plugins, native apps). JaiClaw leads in depth (enterprise security, multi-tenancy, audit, k8s, Spring integration, embeddability, architecture). The parity plan targets closing OpenClaw's breadth advantage while preserving JaiClaw's enterprise strengths.
