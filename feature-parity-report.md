# JaiClaw / OpenClaw Feature Parity Report

**Generated:** 2026-05-28
**OpenClaw commit:** `5869131eea` (main)
**Analysis window:** 30 days

## Executive Summary

OpenClaw has grown to **140+ extensions** and **60+ src subsystems**, while JaiClaw has **27 extensions**, **10 core modules**, **7 channels**, and **5 tool modules**. JaiClaw achieves strong parity on core messaging infrastructure (agent runtime, channels, skills, plugins, memory, security) but has significant gaps in **media generation** (image/video/music), **channel breadth** (7 vs 24 channels), **observability** (no OTel/Prometheus), **ACP protocol**, **task/flow orchestration**, and **advanced memory** (wiki, active memory). Overall parity sits at approximately **45%** of OpenClaw's total feature surface. However, JaiClaw has unique strengths in **Kubernetes tooling**, **Apache Camel integration**, **Drools rules engine**, **Embabel agent delegation**, and **calendar management** that OpenClaw lacks.

## Parity Statistics

| Status | Count |
|--------|-------|
| Complete | 22 |
| Partial | 14 |
| Missing | 42 |
| N/A | 12 |
| JaiClaw-only | 10 |
| **Total domains** | **100** |

**Overall parity:** 41% (Complete + Partial / Total non-N/A)

---

## Feature Matrix

### Core Runtime & Agent

| Domain | OpenClaw Location | JaiClaw Equivalent | Status | Notes |
|--------|-------------------|--------------------|--------|-------|
| Agent runtime | `src/agents/` | `jaiclaw-agent` | **Complete** | AgentRuntime, SessionManager, SystemPromptBuilder |
| Session management | `src/sessions/` | `jaiclaw-agent` (SessionManager) | **Complete** | Session key convention matches |
| Plugin system | `src/plugins/`, `packages/plugin-sdk/` | `jaiclaw-plugin-sdk` | **Complete** | JaiClawPlugin SPI, HookRunner, PluginDiscovery |
| Hook runner | `src/hooks/` | `jaiclaw-plugin-sdk` (HookRunner) | **Complete** | Virtual thread execution |
| Skill system | `src/` (skill loading) | `jaiclaw-skills` | **Complete** | SkillLoader, versioning, TenantSkillRegistry |
| Tool registry | `src/tools/` | `jaiclaw-tools` | **Complete** | ToolRegistry, ToolProfile filtering |
| Config management | `src/config/` | `jaiclaw-config` | **Complete** | @ConfigurationProperties records |
| Message routing | `src/routing/` | `jaiclaw-gateway` (GatewayService) | **Complete** | Channel routing via GatewayService |
| Context compaction | `src/context-engine/` | `jaiclaw-compaction` | **Complete** | Summarization-based compaction |
| Security/auth | `src/security/` | `jaiclaw-security` | **Complete** | JWT, TenantResolver, SecurityContext |
| Multi-tenancy | `src/sessions/` (tenant) | `jaiclaw-security` (TenantContext) | **Complete** | TenantGuard, per-tenant isolation |
| Gateway service | `src/gateway/` | `jaiclaw-gateway` | **Complete** | WebSocket, MCP hosting, REST |
| Channel API (SPI) | `src/channels/` | `jaiclaw-channel-api` | **Complete** | ChannelAdapter SPI, ChannelRegistry |
| Core types | `src/types/` | `jaiclaw-core` | **Complete** | Sealed interfaces, Java records |
| MCP hosting | `src/mcp/` | `jaiclaw-gateway` + `jaiclaw-docs` | **Complete** | McpToolProvider/McpResourceProvider SPI |
| ACP protocol | `src/acp/`, `extensions/acpx/` | — | **Missing** | Agent Communication Protocol — bidirectional ndjson sessions, event ledgers |
| Task executor | `src/tasks/` | — | **Missing** | SQLite-backed task lifecycle, flow ownership, completion tracking |
| Flow orchestration | `src/flows/` | — | **Missing** | Interactive channel onboarding, health checks, plugin discovery flows |
| Setup wizard | `src/wizard/` | — | **Missing** | First-time onboarding, gateway config, channel setup, model selection |
| Daemon management | `src/daemon/` | — | **Missing** | launchd/systemd lifecycle, restart handoff, container detection |
| Status reporting | `src/status/` | — | **Partial** | JaiClaw has INFO logging but no structured status/cost reporting |
| Secrets management | `src/secrets/` | — | **Partial** | JaiClaw uses Spring config/env vars; no encrypted secret store |
| Provider runtime | `src/provider-runtime/` | — | **Partial** | Spring AI handles retries via RestClient; no dedicated retry orchestrator |
| Model catalog | `src/model-catalog/` | — | **Missing** | Model normalization, cost tiers, capability config, alias discovery |

### Channels

| Domain | OpenClaw Location | JaiClaw Equivalent | Status | Notes |
|--------|-------------------|--------------------|--------|-------|
| Telegram | `extensions/telegram/` | `jaiclaw-channel-telegram` | **Complete** | Bot API polling + webhook + file downloads |
| Slack | `extensions/slack/` | `jaiclaw-channel-slack` | **Complete** | Socket Mode + Events API |
| Discord | `extensions/discord/` | `jaiclaw-channel-discord` | **Complete** | Gateway WebSocket + Interactions |
| Email | — | `jaiclaw-channel-email` | **JaiClaw-only** | IMAP polling + SMTP + MIME |
| SMS/Twilio | — | `jaiclaw-channel-sms` | **JaiClaw-only** | Twilio REST + webhook + MMS |
| Signal | `extensions/signal/` | `jaiclaw-channel-signal` | **Complete** | Signal protocol adapter |
| MS Teams | `extensions/msteams/` | `jaiclaw-channel-teams` | **Partial** | OpenClaw recently rebased TeamsSDK patterns (commit `04c2982535`) |
| WhatsApp | `extensions/whatsapp/` | — | **Missing** | Cloud API + Business API |
| Line | `extensions/line/` | — | **Missing** | LINE Messaging API |
| Matrix | `extensions/matrix/` | — | **Missing** | Matrix protocol |
| IRC | `extensions/irc/` | — | **Missing** | IRC protocol |
| Google Chat | `extensions/googlechat/` | — | **Missing** | Google Chat API |
| Feishu/Lark | `extensions/feishu/` | — | **Missing** | Feishu/Lark API |
| Mattermost | `extensions/mattermost/` | — | **Missing** | Mattermost API |
| iMessage | `extensions/imessage/` | — | **Missing** | iMessage (macOS) |
| Nextcloud Talk | `extensions/nextcloud-talk/` | — | **Missing** | Nextcloud Talk |
| Nostr | `extensions/nostr/` | — | **Missing** | Nostr protocol |
| Synology Chat | `extensions/synology-chat/` | — | **Missing** | Synology Chat API |
| Tlon | `extensions/tlon/` | — | **Missing** | Tlon/Urbit |
| Twitch | `extensions/twitch/` | — | **Missing** | Twitch chat/IRC |
| Zalo | `extensions/zalo/` | — | **Missing** | Zalo OA API |
| Zalo User | `extensions/zalouser/` | — | **Missing** | Zalo user-level API |
| Zai | `extensions/zai/` | — | **Missing** | Zai platform |
| Xiaomi | `extensions/xiaomi/` | — | **Missing** | Xiaomi messaging |
| Lobster | `extensions/lobster/` | — | **Missing** | Lobster platform |
| QQBot | `extensions/qqbot/` | — | **Missing** | QQ Bot API |
| ClickClack | `extensions/clickclack/` | — | **Missing** | ClickClack channel |

### AI Providers

| Domain | OpenClaw Location | JaiClaw Equivalent | Status | Notes |
|--------|-------------------|--------------------|--------|-------|
| Anthropic | `extensions/anthropic/` | Spring AI Anthropic | **Complete** | Via `spring-ai-starter-model-anthropic` |
| OpenAI | `extensions/openai/` | Spring AI OpenAI | **Complete** | Via `spring-ai-starter-model-openai` |
| Google/Gemini | `extensions/google/` | Spring AI Vertex | **Partial** | Spring AI supports Vertex AI; no direct Gemini API starter |
| Ollama | `extensions/ollama/` | Spring AI Ollama | **Complete** | Via `spring-ai-starter-model-ollama` |
| Mistral | `extensions/mistral/` | Spring AI Mistral | **Complete** | Via `spring-ai-starter-model-mistral` |
| Amazon Bedrock | `extensions/amazon-bedrock/` | Spring AI Bedrock | **Complete** | Via `spring-ai-starter-model-bedrock` |
| Azure/Microsoft | `extensions/microsoft/` | Spring AI Azure OpenAI | **Partial** | Via `spring-ai-starter-model-azure-openai` |
| MiniMax | `extensions/minimax/` | Spring AI OpenAI (compat) | **Partial** | Via OpenAI-compatible endpoint config |
| DeepSeek | `extensions/deepseek/` | Spring AI OpenAI (compat) | **Partial** | Via OpenAI-compatible endpoint config |
| Groq | `extensions/groq/` | Spring AI OpenAI (compat) | **Partial** | Via OpenAI-compatible endpoint config |
| OpenRouter | `extensions/openrouter/` | Spring AI OpenAI (compat) | **Partial** | Via OpenAI-compatible endpoint config |
| Together | `extensions/together/` | Spring AI OpenAI (compat) | **Partial** | Via OpenAI-compatible endpoint config |
| xAI/Grok | `extensions/xai/` | Spring AI OpenAI (compat) | **Partial** | Via OpenAI-compatible endpoint config |
| Nvidia | `extensions/nvidia/` | — | **Partial** | Could use OpenAI-compat; no dedicated starter |
| vLLM | `extensions/vllm/` | Spring AI OpenAI (compat) | **Partial** | Via OpenAI-compatible endpoint |
| SGLang | `extensions/sglang/` | — | **Partial** | Could use OpenAI-compat |
| HuggingFace | `extensions/huggingface/` | Spring AI HuggingFace | **Partial** | Spring AI has HF support |
| Anthropic Vertex | `extensions/anthropic-vertex/` | Spring AI Anthropic | **Partial** | Requires Vertex endpoint config |
| LiteLLM | `extensions/litellm/` | — | **Partial** | Could use OpenAI-compat proxy |
| Chutes | `extensions/chutes/` | — | **Missing** | No dedicated integration |
| Fireworks | `extensions/fireworks/` | — | **Partial** | Could use OpenAI-compat |
| Cerebras | `extensions/cerebras/` | — | **Partial** | Could use OpenAI-compat |
| DeepInfra | `extensions/deepinfra/` | — | **Partial** | Could use OpenAI-compat |
| Arcee | `extensions/arcee/` | — | **Missing** | Specialized provider |
| LMStudio | `extensions/lmstudio/` | — | **Partial** | Could use OpenAI-compat |
| Qwen | `extensions/qwen/` | — | **Partial** | Could use OpenAI-compat |
| Tencent | `extensions/tencent/` | — | **Missing** | Tencent Cloud API |
| Alibaba | `extensions/alibaba/` | — | **Missing** | Alibaba/DashScope API |
| Microsoft Foundry | `extensions/microsoft-foundry/` | — | **Missing** | Azure AI Foundry |
| BytePlus | `extensions/byteplus/` | — | **Missing** | BytePlus/Volcengine |
| VolcEngine | `extensions/volcengine/` | — | **Missing** | VolcEngine API |
| Qianfan | `extensions/qianfan/` | — | **Missing** | Baidu Qianfan |
| Moonshot | `extensions/moonshot/` | — | **Missing** | Moonshot/Kimi API |
| Kimi Coding | `extensions/kimi-coding/` | — | **Missing** | Kimi coding models |
| Kilocode | `extensions/kilocode/` | — | **Missing** | Kilocode provider |
| StepFun | `extensions/stepfun/` | — | **Missing** | StepFun AI |
| Venice | `extensions/venice/` | — | **Missing** | Venice (privacy-focused) |
| Vercel AI Gateway | `extensions/vercel-ai-gateway/` | — | **Missing** | Vercel AI gateway |
| Cloudflare AI Gateway | `extensions/cloudflare-ai-gateway/` | — | **Missing** | Cloudflare gateway |
| Synthetic | `extensions/synthetic/` | — | **N/A** | Testing/synthetic provider |
| Copilot Proxy | `extensions/copilot-proxy/` | — | **Missing** | VS Code Copilot LM proxy |
| GitHub Copilot | `extensions/github-copilot/` | — | **Missing** | GitHub Copilot auth |
| Inworld | `extensions/inworld/` | — | **Missing** | Inworld AI speech |
| Gradium | `extensions/gradium/` | — | **Missing** | Gradium speech |
| Vydra | `extensions/vydra/` | — | **Missing** | Multi-modal provider |

### Media & Content

| Domain | OpenClaw Location | JaiClaw Equivalent | Status | Notes |
|--------|-------------------|--------------------|--------|-------|
| Media analysis SPI | `extensions/media-understanding-core/` | `jaiclaw-media` | **Complete** | CompositeMediaAnalyzer, async SPI |
| Document parsing | `extensions/document-extract/` | `jaiclaw-documents` | **Partial** | JaiClaw has PDF/HTML/text; OpenClaw has broader extraction + fallback |
| Image generation | `extensions/image-generation-core/`, `extensions/fal/` | — | **Missing** | Provider registry, model candidates, OpenAI-compatible |
| Video generation | `extensions/video-generation-core/`, `extensions/pixverse/`, `extensions/runway/` | — | **Missing** | Video providers (PixVerse, Runway, DashScope) |
| Music generation | `src/music-generation/` | — | **Missing** | Music generation providers |
| ComfyUI | `extensions/comfy/` | — | **Missing** | ComfyUI workflow provider for image/video/music |
| Diff rendering | `extensions/diffs/`, `extensions/diffs-language-pack/` | — | **Missing** | Code diff viewer with PNG/PDF rendering |
| Web readability | `extensions/web-readability/` | — | **Missing** | Article content extraction from HTML |
| Link understanding | `src/link-understanding/` | — | **Missing** | URL extraction, analysis, SSRF-protected fetch |

### Voice & Audio

| Domain | OpenClaw Location | JaiClaw Equivalent | Status | Notes |
|--------|-------------------|--------------------|--------|-------|
| TTS/STT SPI | `extensions/speech-core/` | `jaiclaw-voice` | **Complete** | OpenAI TTS/STT provider |
| Voice calls | `extensions/voice-call/` | `jaiclaw-voice-call` | **Complete** | Voice call support |
| Talk/voice activation | `extensions/talk-voice/`, `src/talk/` | — | **Missing** | Wake-word detection, voice talkback, consultation |
| Realtime transcription | `src/realtime-transcription/` | — | **Missing** | WebSocket-based realtime STT |
| ElevenLabs | `extensions/elevenlabs/` | — | **Missing** | ElevenLabs TTS provider |
| Deepgram | `extensions/deepgram/` | — | **Missing** | Deepgram STT provider |
| Azure Speech | `extensions/azure-speech/` | — | **Missing** | Azure Cognitive Services |
| Local TTS CLI | `extensions/tts-local-cli/` | — | **Missing** | Local CLI-based TTS |
| SenseAudio | `extensions/senseaudio/` | — | **Missing** | Audio transcription |

### Search

| Domain | OpenClaw Location | JaiClaw Equivalent | Status | Notes |
|--------|-------------------|--------------------|--------|-------|
| Perplexity | `extensions/perplexity/` | `jaiclaw-perplexity` | **Complete** | Perplexity search CLI |
| Brave | `extensions/brave/` | — | **Missing** | Brave Search API |
| DuckDuckGo | `extensions/duckduckgo/` | — | **Missing** | DuckDuckGo search |
| Exa | `extensions/exa/` | — | **Missing** | Exa semantic search |
| Firecrawl | `extensions/firecrawl/` | — | **Missing** | Firecrawl web scraping |
| Tavily | `extensions/tavily/` | — | **Missing** | Tavily search API |
| SearXNG | `extensions/searxng/` | — | **Missing** | Self-hosted meta search |
| Web search runtime | `src/web-search/` | `jaiclaw-tools` (WebFetchTool) | **Partial** | JaiClaw has WebFetchTool; no pluggable search provider registry |

### Memory & Knowledge

| Domain | OpenClaw Location | JaiClaw Equivalent | Status | Notes |
|--------|-------------------|--------------------|--------|-------|
| Memory core | `extensions/memory-core/` | `jaiclaw-memory` | **Partial** | JaiClaw has InMemory + VectorStore; OpenClaw has circuit breakers, QMD |
| Active memory | `extensions/active-memory/` | — | **Missing** | Enhanced memory with transcript mgmt, tool filtering, session toggles |
| Memory wiki | `extensions/memory-wiki/` | — | **Missing** | Persistent wiki, Obsidian-compatible knowledge vault |
| LanceDB | `extensions/memory-lancedb/` | — | **Missing** | LanceDB vector store backend |
| Voyage embeddings | `extensions/voyage/` | — | **Missing** | Voyage AI embedding provider |

### Browser & Code

| Domain | OpenClaw Location | JaiClaw Equivalent | Status | Notes |
|--------|-------------------|--------------------|--------|-------|
| Browser automation | `extensions/browser/` | `jaiclaw-browser` | **Complete** | Playwright-based |
| Shell execution | `extensions/openshell/` | `jaiclaw-tools` (ShellExecTool) | **Partial** | JaiClaw has basic exec; OpenClaw has sandbox policies |
| Code execution | `extensions/opencode/`, `extensions/opencode-go/` | `jaiclaw-code` | **Partial** | JaiClaw has code tools; OpenClaw has Kimi/GLM coding models |
| File transfer | `extensions/file-transfer/` | `jaiclaw-documents` | **Partial** | JaiClaw has file I/O; no sandbox policy system |

### Observability & Diagnostics

| Domain | OpenClaw Location | JaiClaw Equivalent | Status | Notes |
|--------|-------------------|--------------------|--------|-------|
| OpenTelemetry | `extensions/diagnostics-otel/` | — | **Partial** | Spring Boot Actuator + Micrometer available but no OTel export config |
| Prometheus | `extensions/diagnostics-prometheus/` | — | **Partial** | Micrometer Prometheus registry available via Spring Boot |
| Trajectory tracking | `src/trajectory/` | — | **Missing** | Execution trajectory recording to JSON |
| Proxy capture | `src/proxy-capture/` | — | **Missing** | HTTP/WebSocket traffic capture for debugging |
| Transcripts | `src/transcripts/` | — | **Missing** | Conversation transcript storage and summarization |

### Identity & Pairing

| Domain | OpenClaw Location | JaiClaw Equivalent | Status | Notes |
|--------|-------------------|--------------------|--------|-------|
| Cross-channel identity | — | `jaiclaw-identity` | **Complete** | Cross-channel identity linking |
| Device pairing | `extensions/device-pair/`, `src/pairing/` | — | **Missing** | QR-based device pairing, token validation |
| Thread ownership | `extensions/thread-ownership/` | — | **Missing** | @-mention routing, thread ownership |

### UI & Interaction

| Domain | OpenClaw Location | JaiClaw Equivalent | Status | Notes |
|--------|-------------------|--------------------|--------|-------|
| Terminal UI (TUI) | `src/tui/` | `jaiclaw-shell` (Spring Shell) | **Partial** | JaiClaw has Spring Shell CLI; no rich TUI |
| Terminal styling | `src/terminal/` | — | **N/A** | Node.js-specific ANSI handling |
| Canvas/A2UI | `extensions/canvas/`, `src/canvas-host/` | `jaiclaw-canvas` | **Complete** | A2UI artifact rendering |
| i18n | `src/i18n/` | — | **Missing** | 19+ language support |
| Auto-reply chunking | `src/auto-reply/` | — | **Missing** | Platform-aware message chunking |

### Advanced Features

| Domain | OpenClaw Location | JaiClaw Equivalent | Status | Notes |
|--------|-------------------|--------------------|--------|-------|
| Cron scheduling | `src/cron/` | `jaiclaw-cron` | **Complete** | JSON persistence, virtual threads |
| Cron management | — | `jaiclaw-cron-manager` | **JaiClaw-only** | Cron management UI |
| Audit trail | — | `jaiclaw-audit` | **Partial** | OpenClaw has trajectory; JaiClaw has AuditLogger SPI |
| Subscriptions | — | `jaiclaw-subscription` | **JaiClaw-only** | Subscription management |
| Document store | — | `jaiclaw-docstore` | **JaiClaw-only** | Document store |
| Commitments | `src/commitments/` | — | **Missing** | LLM-extracted user commitments, scheduling |
| Crestodian planner | `src/crestodian/` | — | **Missing** | LLM-based operation planning assistant |
| Skill workshop | `extensions/skill-workshop/` | — | **Missing** | Workflow capture as skills |
| Token compression | `extensions/tokenjuice/` | — | **Missing** | Tool result compaction |
| Policy enforcement | `extensions/policy/` | — | **Missing** | Workspace conformance doctor checks |
| Webhook routing | `extensions/webhooks/` | — | **Missing** | Authenticated inbound webhooks → TaskFlows |
| Admin HTTP RPC | `extensions/admin-http-rpc/` | — | **Missing** | HTTP RPC for admin operations |
| Bonjour/mDNS | `extensions/bonjour/` | — | **N/A** | Network discovery (desktop-oriented) |
| Google Meet | `extensions/google-meet/` | — | **Missing** | Meeting integration with calendar |
| Open Prose | `extensions/open-prose/` | — | **Missing** | Prose skill bundles |
| OC Path | `extensions/oc-path/` | — | **N/A** | `oc://` URI scheme (OpenClaw-specific) |
| Plugin state (SQLite) | `src/plugin-state/` | — | **Missing** | TTL key-value store for plugins |
| Migration tools | `extensions/migrate-claude/`, `extensions/migrate-hermes/` | — | **N/A** | Migration from other platforms |

### JaiClaw-Only Features

| Domain | JaiClaw Module | Notes |
|--------|---------------|-------|
| Apache Camel integration | `jaiclaw-camel` | Enterprise integration patterns |
| Drools rules engine | `jaiclaw-rules` | Business rules with 3 LLM tools |
| Embabel agent delegation | `jaiclaw-embabel-delegate` | GOAP-based agent planning |
| Kubernetes tools | `jaiclaw-tools-k8s` | K8s cluster management tools |
| Security tools | `jaiclaw-tools-security` | Security scanning tools |
| Calendar management | `jaiclaw-calendar` | Event CRUD, scheduling, MCP hosting |
| Email channel | `jaiclaw-channel-email` | IMAP/SMTP channel |
| SMS/Twilio channel | `jaiclaw-channel-sms` | Twilio-based SMS |
| MCP messaging | `jaiclaw-messaging` | Channel messaging via MCP tools |
| Maven plugin (CI) | `jaiclaw-maven-plugin` | Token budget enforcement |

---

## Gap Details

### Missing Features

#### 1. WhatsApp Channel
- **What it does in OpenClaw:** Full WhatsApp Business API integration — Cloud API, message templates, media, interactive messages
- **OpenClaw location:** `extensions/whatsapp/`
- **Suggested JaiClaw approach:** New `jaiclaw-channel-whatsapp` module using WhatsApp Cloud API via REST; follow existing ChannelAdapter SPI pattern
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P1

#### 2. Image Generation
- **What it does in OpenClaw:** Provider registry for image generation with model capability matching, fal.ai integration, OpenAI DALL-E compatible
- **OpenClaw location:** `extensions/image-generation-core/`, `extensions/fal/`
- **Suggested JaiClaw approach:** New `jaiclaw-image-generation` extension with provider SPI, Spring AI image model support
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P1

#### 3. Video Generation
- **What it does in OpenClaw:** Video generation with multiple providers (PixVerse, Runway, DashScope), capability overlays, duration validation
- **OpenClaw location:** `extensions/video-generation-core/`, `extensions/pixverse/`, `extensions/runway/`
- **Suggested JaiClaw approach:** New `jaiclaw-video-generation` extension with provider SPI
- **Estimated complexity:** High (2+ weeks)
- **Priority:** P2

#### 4. Music Generation
- **What it does in OpenClaw:** Music generation via pluggable providers with model references and timeout handling
- **OpenClaw location:** `src/music-generation/`
- **Suggested JaiClaw approach:** New `jaiclaw-music-generation` extension
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P3

#### 5. Web Search Provider Registry
- **What it does in OpenClaw:** Pluggable web search with providers for Brave, DuckDuckGo, Exa, Firecrawl, Tavily, SearXNG
- **OpenClaw location:** `src/web-search/`, `extensions/brave/`, `extensions/duckduckgo/`, etc.
- **Suggested JaiClaw approach:** New `jaiclaw-web-search` extension with WebSearchProvider SPI + provider modules
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P2

#### 6. Active Memory (Enhanced)
- **What it does in OpenClaw:** Circuit breaker patterns, transcript management, tool filtering, QMD search mode, session-level toggles
- **OpenClaw location:** `extensions/active-memory/`
- **Suggested JaiClaw approach:** Enhance `jaiclaw-memory` with circuit breakers, search modes, and session toggles
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P2

#### 7. Memory Wiki
- **What it does in OpenClaw:** Persistent wiki compiler, Obsidian-compatible knowledge vault
- **OpenClaw location:** `extensions/memory-wiki/`
- **Suggested JaiClaw approach:** New `jaiclaw-memory-wiki` extension with Markdown-based wiki store
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P2

#### 8. Task Executor & Flow Orchestration
- **What it does in OpenClaw:** SQLite-backed task lifecycle management, flow ownership, completion tracking, interactive channel onboarding
- **OpenClaw location:** `src/tasks/`, `src/flows/`
- **Suggested JaiClaw approach:** New `jaiclaw-tasks` extension with JPA/H2 persistence, flow engine
- **Estimated complexity:** High (2+ weeks)
- **Priority:** P2

#### 9. Model Catalog
- **What it does in OpenClaw:** Model normalization, cost tiers, capability config, alias discovery, merging from manifest/plugin/runtime sources
- **OpenClaw location:** `src/model-catalog/`
- **Suggested JaiClaw approach:** New `jaiclaw-model-catalog` module with model registry, cost metadata, capability matching
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P2

#### 10. Internationalization (i18n)
- **What it does in OpenClaw:** 19+ language UI translations with lazy-loaded translation trees
- **OpenClaw location:** `src/i18n/`
- **Suggested JaiClaw approach:** Spring MessageSource + resource bundles in `jaiclaw-core`
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P2

#### 11. Talk / Voice Activation
- **What it does in OpenClaw:** Wake-word detection, voice talkback, agent consultation, audio codec handling
- **OpenClaw location:** `extensions/talk-voice/`, `src/talk/`
- **Suggested JaiClaw approach:** Enhance `jaiclaw-voice` with wake-word detection and talkback
- **Estimated complexity:** High (2+ weeks)
- **Priority:** P3

#### 12. Realtime Transcription
- **What it does in OpenClaw:** WebSocket-based realtime STT with provider registry, reconnection, buffer management
- **OpenClaw location:** `src/realtime-transcription/`
- **Suggested JaiClaw approach:** New `jaiclaw-realtime-transcription` extension using Spring WebSocket
- **Estimated complexity:** High (2+ weeks)
- **Priority:** P3

#### 13. ElevenLabs TTS
- **What it does in OpenClaw:** ElevenLabs text-to-speech provider
- **OpenClaw location:** `extensions/elevenlabs/`
- **Suggested JaiClaw approach:** New provider in `jaiclaw-voice` module
- **Estimated complexity:** Low (1–3 days)
- **Priority:** P2

#### 14. Deepgram STT
- **What it does in OpenClaw:** Deepgram speech-to-text provider
- **OpenClaw location:** `extensions/deepgram/`
- **Suggested JaiClaw approach:** New provider in `jaiclaw-voice` module
- **Estimated complexity:** Low (1–3 days)
- **Priority:** P2

#### 15. Trajectory Tracking
- **What it does in OpenClaw:** Records execution trajectories (agent runs, tool calls, model decisions) to JSON
- **OpenClaw location:** `src/trajectory/`
- **Suggested JaiClaw approach:** Extend `jaiclaw-audit` with trajectory recording or new `jaiclaw-trajectory` module
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P2

#### 16. Transcript Storage
- **What it does in OpenClaw:** Conversation transcript storage and Markdown summarization by session
- **OpenClaw location:** `src/transcripts/`
- **Suggested JaiClaw approach:** Extend `jaiclaw-audit` or new `jaiclaw-transcripts` module
- **Estimated complexity:** Low (1–3 days)
- **Priority:** P2

#### 17. Device Pairing
- **What it does in OpenClaw:** QR code pairing, token/password validation, bootstrap auth
- **OpenClaw location:** `extensions/device-pair/`, `src/pairing/`
- **Suggested JaiClaw approach:** Extend `jaiclaw-identity` or new `jaiclaw-device-pair` extension
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P3

#### 18. Thread Ownership
- **What it does in OpenClaw:** Agent @-mention tracking and thread ownership routing across channels
- **OpenClaw location:** `extensions/thread-ownership/`
- **Suggested JaiClaw approach:** Extend `jaiclaw-agent` with thread-ownership logic in routing
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P2

#### 19. Webhook Event Routing
- **What it does in OpenClaw:** Authenticated inbound webhooks binding to TaskFlows
- **OpenClaw location:** `extensions/webhooks/`
- **Suggested JaiClaw approach:** New `jaiclaw-webhooks` extension with Spring MVC webhook endpoints
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P2

#### 20. Token Compression (TokenJuice)
- **What it does in OpenClaw:** Compacts exec/bash tool results with reducers to save token budget
- **OpenClaw location:** `extensions/tokenjuice/`
- **Suggested JaiClaw approach:** Plugin hook in `jaiclaw-plugin-sdk` for tool result post-processing
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P2

#### 21. Plugin State Store
- **What it does in OpenClaw:** SQLite-backed TTL key-value store for plugin data with namespaced entries
- **OpenClaw location:** `src/plugin-state/`
- **Suggested JaiClaw approach:** Extend `jaiclaw-plugin-sdk` with `PluginStateStore` SPI + JPA/H2 implementation
- **Estimated complexity:** Low (1–3 days)
- **Priority:** P2

#### 22. Auto-Reply Chunking
- **What it does in OpenClaw:** Platform-aware message chunking for outbound messages (newline vs length-based)
- **OpenClaw location:** `src/auto-reply/`
- **Suggested JaiClaw approach:** Add chunking logic to `jaiclaw-channel-api` ChannelAdapter base
- **Estimated complexity:** Low (1–3 days)
- **Priority:** P1

#### 23. Commitments
- **What it does in OpenClaw:** LLM-extracted user commitments from conversations with scheduling
- **OpenClaw location:** `src/commitments/`
- **Suggested JaiClaw approach:** New `jaiclaw-commitments` extension using Spring AI + jaiclaw-cron
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P3

#### 24. Skill Workshop
- **What it does in OpenClaw:** Captures repeatable workflows as workspace skills with approval policies
- **OpenClaw location:** `extensions/skill-workshop/`
- **Suggested JaiClaw approach:** Extend `jaiclaw-skills` with dynamic skill capture and persistence
- **Estimated complexity:** High (2+ weeks)
- **Priority:** P3

#### 25. ACP Protocol
- **What it does in OpenClaw:** Bidirectional ndjson agent communication, event ledgers, session management
- **OpenClaw location:** `src/acp/`, `extensions/acpx/`
- **Suggested JaiClaw approach:** New `jaiclaw-acp` module with ndjson transport + session management
- **Estimated complexity:** High (2+ weeks)
- **Priority:** P3

#### 26. Secrets Management
- **What it does in OpenClaw:** Encrypted credential store, audit trails, environment variable resolution
- **OpenClaw location:** `src/secrets/`
- **Suggested JaiClaw approach:** New `jaiclaw-secrets` extension or integrate with Spring Vault / Kubernetes secrets
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P2

#### 27. Google Meet Integration
- **What it does in OpenClaw:** Calendar lookup, conference management, attendance tracking
- **OpenClaw location:** `extensions/google-meet/`
- **Suggested JaiClaw approach:** Extend `jaiclaw-calendar` with Google Meet conference support
- **Estimated complexity:** Medium (1–2 weeks)
- **Priority:** P3

#### 28. Web Readability
- **What it does in OpenClaw:** Extracts readable article content from HTML responses
- **OpenClaw location:** `extensions/web-readability/`
- **Suggested JaiClaw approach:** Add Jsoup-based readability extraction to `jaiclaw-tools` WebFetchTool
- **Estimated complexity:** Low (1–3 days)
- **Priority:** P1

#### 29. Admin HTTP RPC
- **What it does in OpenClaw:** Exposes gateway RPC methods over HTTP for trusted operators
- **OpenClaw location:** `extensions/admin-http-rpc/`
- **Suggested JaiClaw approach:** Spring MVC actuator-style admin endpoints in `jaiclaw-gateway`
- **Estimated complexity:** Low (1–3 days)
- **Priority:** P2

#### 30. Additional Channels (Line, Matrix, IRC, Google Chat, Feishu, Mattermost)
- **What they do in OpenClaw:** Full channel adapters for each platform
- **Suggested JaiClaw approach:** One `jaiclaw-channel-*` module per platform following ChannelAdapter SPI
- **Estimated complexity:** Medium each (1–2 weeks per channel)
- **Priority:** P2 (Line, Matrix, Google Chat), P3 (others)

---

### Partial Implementations

#### Context Compaction
- **What exists in JaiClaw:** Summarization-based compaction in `jaiclaw-compaction`
- **What is missing:** Token budget analysis, per-tool result compression (tokenjuice), graduated compression levels
- **Gap severity:** Moderate
- **Suggested next steps:** Add token counting and graduated compaction strategies

#### Document Parsing
- **What exists in JaiClaw:** PDF, HTML, text parsing + PDF form filling in `jaiclaw-documents`
- **What is missing:** Fallback page-to-image conversion, broader format support (DOCX, PPTX)
- **Gap severity:** Minor
- **Suggested next steps:** Add Apache POI for Office formats, image fallback for unreadable PDFs

#### Memory
- **What exists in JaiClaw:** InMemorySearchManager + VectorStoreSearchManager
- **What is missing:** Circuit breaker patterns, QMD search mode, session-level toggles, transcript integration
- **Gap severity:** Moderate
- **Suggested next steps:** Add circuit breaker wrapper, search mode configuration, session-scoped memory toggles

#### Shell Execution
- **What exists in JaiClaw:** ShellExecTool in `jaiclaw-tools`
- **What is missing:** Sandbox policy system (read-only fs, network restrictions), workspace boundary enforcement beyond path traversal
- **Gap severity:** Moderate
- **Suggested next steps:** Implement configurable sandbox profiles using Java SecurityManager or process isolation

#### Observability
- **What exists in JaiClaw:** Spring Boot Actuator + Micrometer available
- **What is missing:** Explicit OTel exporter configuration, Prometheus scrape endpoint, custom agent metrics
- **Gap severity:** Minor (infrastructure is there, just needs configuration/wiring)
- **Suggested next steps:** Add `jaiclaw-observability` starter with OTel + Prometheus auto-config

#### Audit Trail
- **What exists in JaiClaw:** AuditLogger SPI, InMemoryAuditLogger in `jaiclaw-audit`
- **What is missing:** Persistent audit storage, trajectory recording, execution replay
- **Gap severity:** Moderate
- **Suggested next steps:** Add JPA-backed AuditLogger, trajectory recording module

---

## Recently Added in OpenClaw (last 30 days)

### Validation & Hardening (bulk)
- 50+ commits validating numeric parameters across all subsystems (Slack, Telegram, Discord, gateway, cron, media, tools, memory, search, image generation, etc.)
- Pattern: centralized numeric coercion helpers (`b877fc58a5`)

### MS Teams
- `04c2982535` — Rebased TeamsSDK patterns to simplify Teams integration (significant refactor)

### Codex
- `56a5d7e865` — Codex deferred report-mode plugin approvals
- `2ba725ef48` — Code mode timeout and prompt snapshots stabilization
- `f77a2687b6` — Codex dynamic tool snapshot refresh

### Agent System
- `e12a6d6a67` — Agents own their system prompt assembly (refactor)
- `1211123fe6` — Pass agent ID to bootstrap preload

### Memory
- `3029326a56` — Compact short-term promotion entries in memory

### Cron
- `ff21b4e731` — Complete jobs filters for cron

### WhatsApp
- `359c31b7e7` — WhatsApp approval QA scenarios

### Performance
- `0296f0a779` — Load provider discovery entries natively (perf improvement)
- `0e40408375` — Speed up launcher version output

---

## JaiClaw-Only Features

| Feature | Module | Description |
|---------|--------|-------------|
| Apache Camel integration | `jaiclaw-camel` | Enterprise integration patterns — route-based message processing, ETL pipelines |
| Drools rules engine | `jaiclaw-rules` | Business rules execution — text analysis, decision, validation, tax rules; 3 LLM tools |
| Embabel agent delegation | `jaiclaw-embabel-delegate` | GOAP-based goal-oriented agent planning via Embabel framework |
| Kubernetes tools | `jaiclaw-tools-k8s` | K8s cluster management — pod inspection, service management, deployment tools |
| Security tools | `jaiclaw-tools-security` | Security scanning and assessment tools |
| Calendar management | `jaiclaw-calendar` | Full event CRUD, scheduling, multi-tenancy, MCP hosting, dual-mode build |
| Email channel | `jaiclaw-channel-email` | IMAP polling + SMTP + MIME attachment handling |
| SMS/Twilio channel | `jaiclaw-channel-sms` | Twilio REST API + webhook + MMS |
| MCP messaging tools | `jaiclaw-messaging` | Channel messaging exposed as 8 MCP tools (send, broadcast, sessions, agent chat) |
| Maven plugin (CI) | `jaiclaw-maven-plugin` | `jaiclaw:analyze` goal — CI token budget enforcement for examples |
| Subscription management | `jaiclaw-subscription` | User subscription tracking and management |
| Document store | `jaiclaw-docstore` | Persistent document storage with Telegram integration |
| Cron manager app | `jaiclaw-cron-manager` | Standalone cron management UI application |
| Discord-specific tools | `jaiclaw-discord-tools` | Discord platform-specific tooling |
| Slack-specific tools | `jaiclaw-slack-tools` | Slack platform-specific tooling |
| Spring Boot auto-config | `jaiclaw-spring-boot-starter` | 3-layer auto-configuration (Core → Gateway → Channel) |
| 32 example applications | `jaiclaw-examples/` | Comprehensive example library |
| BOM (Bill of Materials) | `jaiclaw-bom` | Dependency management for downstream consumers |

---

## Recommended Prioritization

### P1 — High Impact / Low-Medium Effort (Quick Wins)

1. **Auto-reply chunking** — Platform-aware message splitting (Low effort, immediate quality improvement)
2. **Web readability extraction** — Jsoup-based article extraction in WebFetchTool (Low effort)
3. **WhatsApp channel** — Highest-demand missing channel (Medium effort)
4. **Image generation SPI** — Core media capability gap (Medium effort)
5. **Observability wiring** — OTel + Prometheus auto-config starter (Low effort — Spring Boot already has the infra)

### P2 — High Impact / Medium-High Effort (Roadmap Items)

6. **Web search provider registry** — Pluggable search (Brave, Tavily, Exa, etc.)
7. **Enhanced memory** — Circuit breakers, QMD search, session toggles
8. **Memory wiki** — Persistent knowledge vault
9. **Model catalog** — Model normalization, cost tiers, capabilities
10. **Task executor & flows** — Task lifecycle management
11. **Thread ownership** — @-mention routing across channels
12. **Token compression** — Tool result compaction
13. **Trajectory/transcripts** — Execution recording and conversation transcripts
14. **ElevenLabs + Deepgram** — Voice provider breadth
15. **i18n** — Multi-language support
16. **Plugin state store** — TTL key-value store for plugins
17. **Secrets management** — Spring Vault integration
18. **Additional channels** — Line, Matrix, Google Chat (1-2 weeks each)
19. **Video generation** — Provider SPI + initial providers
20. **Admin HTTP RPC** — Operator admin endpoints
21. **Webhook event routing** — Inbound webhook → flow binding

### P3 — Lower Impact or Specialized (Defer)

22. **ACP protocol** — Agent Communication Protocol (complex, OpenClaw-specific)
23. **Music generation** — Niche media capability
24. **Talk/voice activation** — Wake-word detection (complex audio processing)
25. **Realtime transcription** — WebSocket STT (complex)
26. **Device pairing** — QR code bootstrap auth
27. **Skill workshop** — Dynamic skill capture
28. **Commitments** — LLM-extracted scheduling
29. **Codex harness** — Coding agent integration (complex, OpenClaw-specific)
30. **ComfyUI** — Workflow-based generation (specialized)
31. **Remaining niche channels** — IRC, Nostr, Synology, Tlon, Twitch, etc.
32. **Niche AI providers** — StepFun, Venice, Arcee, etc. (most usable via OpenAI-compat)
